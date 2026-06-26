package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegion
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.Covering
import com.example.api.domain.studbook.model.breeding.CoveringCertificateNumber
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import java.time.LocalDate
import java.time.Year
import org.springframework.stereotype.Repository

/**
 * 検証済みで保存された VO 値を復元時に取り出すヘルパー。`create` が `Result` を返す VO を、DB 由来の trusted データとして失敗しない前提で取り出す（Err
 * は復元データ破損を示す `IllegalStateException`）。
 */
private fun <V, E> Result<V, E>.orThrow(): V = getOrThrow {
    IllegalStateException("永続化された値の復元に失敗しました: $it")
}

/**
 * ドメインポート [BreedingResultRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [BreedingResult] と永続化モデル [BreedingResultRow] を手書きマッパーで相互変換し、CRUD は
 * [BreedingResultSpringDataRepository] へ委譲する。value class ID・nullable な種付 [Covering] のフラット化・ sealed
 * な分娩結果 [FoalingOutcome] の判別子フラット化も本マッパーが担う（永続化モデルを分離した帰結。ADR-0027）。
 *
 * 永続化実装は JDBC 一本に統一し、起動 datasource を H2(dev / Cloud Run) ↔ PostgreSQL(本番) で差し替える方針のため、 InMemory
 * 実装・プロファイル切替は持たない（ADR-0030）。デフォルト（H2・PostgreSQL 互換）でも本クラスが配線される。
 */
@Repository
class JdbcBreedingResultRepository(private val rows: BreedingResultSpringDataRepository) :
    BreedingResultRepository {

    override fun findById(id: BreedingResultId): BreedingResult? =
        rows.findById(id.value).map { it.toDomain() }.orElse(null)

    override fun findByBreedingRegistrationIdAndBreedingYear(
        breedingRegistrationId: BreedingRegistrationId,
        breedingYear: Year,
    ): BreedingResult? =
        rows
            .findByBreedingRegistrationIdAndBreedingYear(
                breedingRegistrationId.value,
                breedingYear.value,
            )
            ?.toDomain()

    override fun save(breedingResult: BreedingResult): BreedingResult =
        rows.save(breedingResult.toRow()).toDomain()

    /** 永続化モデルからドメイン集約を再構成する（検証・採番なし。covering/区分の整合は集約の init が保証）。 */
    private fun BreedingResultRow.toDomain(): BreedingResult =
        BreedingResult.reconstitute(
            id = BreedingResultId(id),
            breedingRegistrationId = BreedingRegistrationId(breedingRegistrationId),
            breedingYear = Year.of(breedingYear),
            covering = toCovering(),
            outcome = toOutcome(),
        )

    /** 種付列（coveringDate の有無を判別子とする）から nullable な [Covering] を復元する。 */
    private fun BreedingResultRow.toCovering(): Covering? = coveringDate?.let { date ->
        Covering(
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
            OUTCOME_LIVE_FOAL ->
                FoalingOutcome.LiveFoal(checkNotNull(outcomeFoalingDate) { "生産の分娩日が欠落: id=$id" })
            OUTCOME_NOT_CONCEIVED -> FoalingOutcome.NotConceived
            OUTCOME_ABORTION -> FoalingOutcome.Abortion
            OUTCOME_TWIN_ABORTION -> FoalingOutcome.TwinAbortion
            OUTCOME_STILLBIRTH -> FoalingOutcome.Stillbirth
            OUTCOME_TWIN_STILLBIRTH -> FoalingOutcome.TwinStillbirth
            OUTCOME_NEONATAL_DEATH -> FoalingOutcome.NeonatalDeath
            OUTCOME_TWIN_NEONATAL_DEATH -> FoalingOutcome.TwinNeonatalDeath
            OUTCOME_NOT_COVERED -> FoalingOutcome.NotCovered
            else -> error("未知の outcome_type です: $outcomeType (id=$id)")
        }

    /** ドメイン集約を永続化モデルへ写す。version はドメインが持たないため常に null（insert 判定。更新系は #424）。 */
    private fun BreedingResult.toRow(): BreedingResultRow {
        val (outcomeType, foalingDate) = outcome.toTypeAndDate()
        return BreedingResultRow(
            id = id.value,
            breedingRegistrationId = breedingRegistrationId.value,
            breedingYear = breedingYear.value,
            coveringStallionId = covering?.stallionId?.value,
            coveringDate = covering?.coveringDate,
            coveringPlace = covering?.coveringPlace?.value,
            coveringCertificateNumber = covering?.certificateNumber?.value,
            outcomeType = outcomeType,
            outcomeFoalingDate = foalingDate,
        )
    }

    /** sealed [FoalingOutcome]（null=未報告）を判別子と分娩日のペアへ写す。 */
    private fun FoalingOutcome?.toTypeAndDate(): Pair<String?, LocalDate?> =
        when (this) {
            null -> null to null
            is FoalingOutcome.LiveFoal -> OUTCOME_LIVE_FOAL to foalingDate
            FoalingOutcome.NotConceived -> OUTCOME_NOT_CONCEIVED to null
            FoalingOutcome.Abortion -> OUTCOME_ABORTION to null
            FoalingOutcome.TwinAbortion -> OUTCOME_TWIN_ABORTION to null
            FoalingOutcome.Stillbirth -> OUTCOME_STILLBIRTH to null
            FoalingOutcome.TwinStillbirth -> OUTCOME_TWIN_STILLBIRTH to null
            FoalingOutcome.NeonatalDeath -> OUTCOME_NEONATAL_DEATH to null
            FoalingOutcome.TwinNeonatalDeath -> OUTCOME_TWIN_NEONATAL_DEATH to null
            FoalingOutcome.NotCovered -> OUTCOME_NOT_COVERED to null
        }

    private companion object {
        const val OUTCOME_LIVE_FOAL = "LIVE_FOAL"
        const val OUTCOME_NOT_CONCEIVED = "NOT_CONCEIVED"
        const val OUTCOME_ABORTION = "ABORTION"
        const val OUTCOME_TWIN_ABORTION = "TWIN_ABORTION"
        const val OUTCOME_STILLBIRTH = "STILLBIRTH"
        const val OUTCOME_TWIN_STILLBIRTH = "TWIN_STILLBIRTH"
        const val OUTCOME_NEONATAL_DEATH = "NEONATAL_DEATH"
        const val OUTCOME_TWIN_NEONATAL_DEATH = "TWIN_NEONATAL_DEATH"
        const val OUTCOME_NOT_COVERED = "NOT_COVERED"
    }
}
