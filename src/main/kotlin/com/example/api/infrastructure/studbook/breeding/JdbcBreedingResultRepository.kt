package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.BreedingRegion
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.Covering
import com.example.api.domain.studbook.model.breeding.CoveringCertificateNumber
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.breeding.ForeignCoveringProofNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSire
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSireName
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.infrastructure.shared.lockRowIfVersionMatches
import com.example.api.infrastructure.shared.orThrow
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * ドメインポート [BreedingResultRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [BreedingResult] と永続化モデル [BreedingResultRow] を手書きマッパーで相互変換し、CRUD は
 * [BreedingResultSpringDataRepository] へ委譲する。value class ID・nullable な種付 [Covering] のフラット化・ sealed
 * な分娩結果 [FoalingOutcome] の判別子フラット化も本マッパーが担う（永続化モデルを分離した帰結。ADR-0027）。
 *
 * 永続化実装は JDBC 一本に統一し、datasource は実行環境が外部供給する（本番 = Prisma Postgres の env 注入、ローカル = docker-compose の
 * PostgreSQL。H2 全面脱却・#451）ため、InMemory 実装・プロファイル切替は持たない（ADR-0030）。
 */
@Repository
class JdbcBreedingResultRepository(
    private val rows: BreedingResultSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : BreedingResultRepository {

    override fun findById(worldId: WorldId, id: BreedingResultId): BreedingResult? =
        rows.findByWorldIdAndId(worldId.value, id.value)?.toDomain()

    override fun findByBreedingRegistrationIdAndBreedingYear(
        worldId: WorldId,
        breedingRegistrationId: BreedingRegistrationId,
        breedingYear: Year,
    ): BreedingResult? =
        rows
            .findByWorldIdAndBreedingRegistrationIdAndBreedingYear(
                worldId.value,
                breedingRegistrationId.value,
                breedingYear.value,
            )
            ?.toDomain()

    /**
     * 版が一致するときだけ更新する（競合は例外にせず [UpdateConflict] で返す。#867）。
     *
     * insert（version が null）はロック不要。update のときだけ [lockRowIfVersionMatches] で行をロックして版を
     * 突き合わせ、一致した場合にのみ `save` を通す（例外に頼れない理由はヘルパーの KDoc）。
     */
    override fun save(
        worldId: WorldId,
        breedingResult: BreedingResult,
    ): Result<BreedingResult, UpdateConflict> {
        val version = breedingResult.version
        val id = breedingResult.id.value
        // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う
        if (version != null && !jdbcClient.lockRowIfVersionMatches(TABLE, id, version)) {
            return Err(UpdateConflict)
        }
        return Ok(rows.save(breedingResult.toRow(worldId)).toDomain())
    }

    /** 永続化モデルからドメイン集約を再構成する（検証・採番なし。covering/区分の整合は集約の init が保証）。 */
    private fun BreedingResultRow.toDomain(): BreedingResult =
        BreedingResult.reconstitute(
            id = BreedingResultId(id),
            breedingRegistrationId = BreedingRegistrationId(breedingRegistrationId),
            breedingYear = Year.of(breedingYear),
            covering = toCovering(),
            outcome = toOutcome(),
            reportSubmittedOn = reportSubmittedOn,
            version = version,
        )

    /** 判別子 `covering_type` と各バリアント列から nullable な [Covering] を復元する（種付せずの年は null）。 */
    private fun BreedingResultRow.toCovering(): Covering? =
        when (coveringType) {
            null -> null
            CoveringType.DOMESTIC -> toDomesticCovering()
            CoveringType.OVERSEAS -> toOverseasCovering()
            else -> error("未知の種付の判別子: $coveringType (id=$id)")
        }

    private fun BreedingResultRow.toOverseasCovering(): Covering.Overseas {
        val date = checkNotNull(coveringDate) { "海外種付の種付日が欠落: id=$id" }
        val name = checkNotNull(coveringForeignSireName) { "外国種牡馬の馬名が欠落: id=$id" }
        val country = checkNotNull(coveringForeignSireStudBookCountry) { "登録国が欠落: id=$id" }
        val breed = checkNotNull(coveringForeignSireBreedType) { "品種が欠落: id=$id" }
        val coat = checkNotNull(coveringForeignSireCoatColor) { "毛色が欠落: id=$id" }
        val place = checkNotNull(this.coveringCountry) { "種付国が欠落: id=$id" }
        val proof = checkNotNull(coveringProofNumber) { "種付事実の証明の番号が欠落: id=$id" }
        return Covering.Overseas(
            sire =
                ForeignSire(
                    name = ForeignSireName.create(name).orThrow(),
                    studBookCountry = OriginCountry.create(country).orThrow(),
                    breedType = BreedType.valueOf(breed),
                    coatColor = CoatColor.valueOf(coat),
                ),
            coveringDate = date,
            coveringCountry = OriginCountry.create(place).orThrow(),
            proofNumber = ForeignCoveringProofNumber.create(proof).orThrow(),
        )
    }

    private fun BreedingResultRow.toDomesticCovering(): Covering.Domestic {
        val date = checkNotNull(coveringDate) { "国内種付の種付日が欠落: id=$id" }
        return Covering.Domestic(
            stallionId = BloodHorseId(checkNotNull(coveringStallionId) { "種付の種牡馬IDが欠落: id=$id" }),
            coveringDate = date,
            coveringPlace = coveringPlace?.let { BreedingRegion.create(it).orThrow() },
            certificateNumber =
                CoveringCertificateNumber.create(
                        checkNotNull(coveringCertificateNumber) { "種付証明書番号が欠落: id=$id" }
                    )
                    .orThrow(),
        )
    }

    /** 判別子 [BreedingResultRow.outcomeType] から sealed [FoalingOutcome] を復元する（未報告なら null）。 */
    private fun BreedingResultRow.toOutcome(): FoalingOutcome? =
        when (outcomeType) {
            null -> null
            OutcomeType.LIVE_FOAL ->
                FoalingOutcome.LiveFoal(checkNotNull(outcomeFoalingDate) { "生産の分娩日が欠落: id=$id" })
            OutcomeType.NOT_CONCEIVED -> FoalingOutcome.NotConceived
            OutcomeType.ABORTION -> FoalingOutcome.Abortion
            OutcomeType.TWIN_ABORTION -> FoalingOutcome.TwinAbortion
            OutcomeType.STILLBIRTH -> FoalingOutcome.Stillbirth
            OutcomeType.TWIN_STILLBIRTH -> FoalingOutcome.TwinStillbirth
            OutcomeType.NEONATAL_DEATH -> FoalingOutcome.NeonatalDeath
            OutcomeType.TWIN_NEONATAL_DEATH -> FoalingOutcome.TwinNeonatalDeath
            OutcomeType.NOT_COVERED -> FoalingOutcome.NotCovered
            else -> error("未知の outcome_type です: $outcomeType (id=$id)")
        }

    /**
     * ドメイン集約を永続化モデルへ写す。
     *
     * version は集約が保持する値をそのまま写す（null なら Spring Data JDBC が新規と判定して insert、非 null なら 楽観ロック付き
     * update。ADR-0027 の落とし穴②③）。
     */
    private fun BreedingResult.toRow(worldId: WorldId): BreedingResultRow {
        val (outcomeType, foalingDate) = outcome.toTypeAndDate()
        val domestic = covering as? Covering.Domestic
        val overseas = covering as? Covering.Overseas
        val coveringType =
            when (covering) {
                is Covering.Domestic -> CoveringType.DOMESTIC
                is Covering.Overseas -> CoveringType.OVERSEAS
                null -> null
            }
        return BreedingResultRow(
            id = id.value,
            worldId = worldId.value,
            breedingRegistrationId = breedingRegistrationId.value,
            breedingYear = breedingYear.value,
            coveringType = coveringType,
            coveringStallionId = domestic?.stallionId?.value,
            coveringDate = covering?.coveringDate,
            coveringPlace = domestic?.coveringPlace?.value,
            coveringCertificateNumber = domestic?.certificateNumber?.value,
            coveringForeignSireName = overseas?.sire?.name?.value,
            coveringForeignSireStudBookCountry = overseas?.sire?.studBookCountry?.name,
            coveringForeignSireBreedType = overseas?.sire?.breedType?.name,
            coveringForeignSireCoatColor = overseas?.sire?.coatColor?.name,
            coveringCountry = overseas?.coveringCountry?.name,
            coveringProofNumber = overseas?.proofNumber?.value,
            outcomeType = outcomeType,
            outcomeFoalingDate = foalingDate,
            reportSubmittedOn = reportSubmittedOn,
            version = version,
        )
    }

    /** sealed [FoalingOutcome]（null=未報告）を判別子と分娩日のペアへ写す。 */
    private fun FoalingOutcome?.toTypeAndDate(): Pair<String?, LocalDate?> =
        when (this) {
            null -> null to null
            is FoalingOutcome.LiveFoal -> OutcomeType.LIVE_FOAL to foalingDate
            FoalingOutcome.NotConceived -> OutcomeType.NOT_CONCEIVED to null
            FoalingOutcome.Abortion -> OutcomeType.ABORTION to null
            FoalingOutcome.TwinAbortion -> OutcomeType.TWIN_ABORTION to null
            FoalingOutcome.Stillbirth -> OutcomeType.STILLBIRTH to null
            FoalingOutcome.TwinStillbirth -> OutcomeType.TWIN_STILLBIRTH to null
            FoalingOutcome.NeonatalDeath -> OutcomeType.NEONATAL_DEATH to null
            FoalingOutcome.TwinNeonatalDeath -> OutcomeType.TWIN_NEONATAL_DEATH to null
            FoalingOutcome.NotCovered -> OutcomeType.NOT_COVERED to null
        }

    private companion object {
        /** 楽観ロックの行ロックを掛ける対象（[lockRowIfVersionMatches] に渡す完全修飾名）。 */
        const val TABLE = "studbook.breeding_result"
    }
}
