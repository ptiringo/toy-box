package com.example.api.infrastructure.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.Breeder
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.LandingDate
import com.example.api.domain.studbook.model.horse.bloodhorse.MicrochipNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import org.springframework.stereotype.Repository

/**
 * 検証済みで保存された VO 値を復元時に取り出すヘルパー。`create` が `Result` を返す VO を、DB 由来の trusted データとして失敗しない前提で取り出す（Err
 * は復元データ破損を示す `IllegalStateException`）。
 */
private fun <V, E> Result<V, E>.orThrow(): V = getOrThrow {
    IllegalStateException("永続化された値の復元に失敗しました: $it")
}

/**
 * ドメインポート [BloodHorseRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [BloodHorse] と永続化モデル [BloodHorseRow] を手書きマッパーで相互変換し、CRUD は
 * [BloodHorseSpringDataRepository] へ委譲する。value class ID・各種 VO（生成口が `create`＝`Result` のもの）・enum・
 * sealed な出自 [Origin] の判別子フラット化も本マッパーが担う（永続化モデルを分離した帰結。ADR-0027）。
 *
 * 永続化実装は JDBC 一本に統一し、起動 datasource を H2(dev / Cloud Run) ↔ PostgreSQL(本番) で差し替える方針のため、 InMemory
 * 実装・プロファイル切替は持たない（ADR-0030）。デフォルト（H2・PostgreSQL 互換）でも本クラスが配線される。
 */
@Repository
class JdbcBloodHorseRepository(private val rows: BloodHorseSpringDataRepository) :
    BloodHorseRepository {

    override fun findById(id: BloodHorseId): BloodHorse? =
        rows.findById(id.value).map { it.toDomain() }.orElse(null)

    override fun findAllById(ids: Set<BloodHorseId>): Map<BloodHorseId, BloodHorse> =
        rows.findAllById(ids.map { it.value }).associate {
            val horse = it.toDomain()
            horse.id to horse
        }

    override fun save(bloodHorse: BloodHorse): BloodHorse = rows.save(bloodHorse.toRow()).toDomain()

    /**
     * 永続化モデルからドメイン集約を再構成する（検証・採番なし）。
     *
     * 生成口が `create`（`Result`）の VO は保存済みの検証済み値だが、復元時はここで再構成して失敗しない前提で 取り出す（DB 由来の trusted
     * データ。infrastructure 層は例外送出が許容される）。出自は判別子 [BloodHorseRow.originType] から sealed [Origin] を復元する。
     */
    private fun BloodHorseRow.toDomain(): BloodHorse =
        BloodHorse.reconstitute(
            id = BloodHorseId(id),
            registrationNumber = PedigreeRegistrationNumber.create(registrationNumber).orThrow(),
            sex = Sex.valueOf(sex),
            coatColor = CoatColor.valueOf(coatColor),
            breedType = BreedType.valueOf(breedType),
            dateOfBirth = DateOfBirth(dateOfBirth),
            breeder = Breeder.create(breeder).orThrow(),
            microchipNumber = MicrochipNumber.create(microchipNumber).orThrow(),
            origin = toOrigin(),
            name = name?.let { HorseName.create(it).orThrow() },
        )

    /** 判別子と各バリアント列から sealed [Origin] を復元する。 */
    private fun BloodHorseRow.toOrigin(): Origin =
        when (originType) {
            ORIGIN_DOMESTIC ->
                Origin.Domestic(
                    sireId = BloodHorseId(checkNotNull(sireId) { "内国産の父IDが欠落: id=$id" }),
                    damId = BloodHorseId(checkNotNull(damId) { "内国産の母IDが欠落: id=$id" }),
                )
            ORIGIN_IMPORTED ->
                Origin.Imported(
                    originCountry =
                        OriginCountry.create(checkNotNull(originCountry) { "輸入の原産国が欠落: id=$id" })
                            .orThrow(),
                    landingDate = LandingDate(checkNotNull(landingDate) { "輸入の揚陸日が欠落: id=$id" }),
                )
            else -> error("未知の origin_type です: $originType (id=$id)")
        }

    /** ドメイン集約を永続化モデルへ写す。version はドメインが持たないため常に null（insert 判定。更新系は #424）。 */
    private fun BloodHorse.toRow(): BloodHorseRow {
        val base =
            BloodHorseRow(
                id = id.value,
                registrationNumber = registrationNumber.value,
                sex = sex.name,
                coatColor = coatColor.name,
                breedType = breedType.name,
                dateOfBirth = dateOfBirth.value,
                breeder = breeder.name,
                microchipNumber = microchipNumber.value,
                name = name?.value,
                originType = "",
            )
        return when (val o = origin) {
            is Origin.Domestic ->
                base.copy(
                    originType = ORIGIN_DOMESTIC,
                    sireId = o.sireId.value,
                    damId = o.damId.value,
                )
            is Origin.Imported ->
                base.copy(
                    originType = ORIGIN_IMPORTED,
                    originCountry = o.originCountry.name,
                    landingDate = o.landingDate.value,
                )
        }
    }

    private companion object {
        const val ORIGIN_DOMESTIC = "DOMESTIC"
        const val ORIGIN_IMPORTED = "IMPORTED"
    }
}
