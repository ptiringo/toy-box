package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.Versioned
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository

/**
 * ドメインポート [BreedingRegistrationRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [BreedingRegistration] と永続化モデル [BreedingRegistrationRow] を手書きマッパーで相互変換し、CRUD は
 * [BreedingRegistrationSpringDataRepository] へ委譲する。value class の各種 ID（`BreedingRegistrationId` /
 * `BloodHorseId`）↔ DB `uuid` 列、enum（`BreedingRole` / `RetirementReason`）↔ 文字列列、nullable な供用停止
 * （`BreedingRetirement`）↔ 2 列のフラット化も本マッパーが担う（永続化モデルを分離した帰結。ADR-0027）。
 *
 * 永続化実装は JDBC 一本に統一し、起動 datasource を H2(dev / Cloud Run) ↔ PostgreSQL(本番) で差し替える方針のため、 InMemory
 * 実装・プロファイル切替は持たない（ADR-0030）。デフォルト（H2・PostgreSQL 互換）でも本クラスが配線される。
 */
@Repository
class JdbcBreedingRegistrationRepository(
    private val rows: BreedingRegistrationSpringDataRepository
) : BreedingRegistrationRepository {

    override fun findById(id: BreedingRegistrationId): Versioned<BreedingRegistration>? =
        rows.findById(id.value).map { it.toVersioned() }.orElse(null)

    override fun save(breedingRegistration: BreedingRegistration): BreedingRegistration =
        rows.save(breedingRegistration.toRow()).toDomain()

    override fun update(
        versioned: Versioned<BreedingRegistration>
    ): Result<Versioned<BreedingRegistration>, UpdateConflict> =
        try {
            Ok(rows.save(versioned.value.toRow(version = versioned.version)).toVersioned())
        } catch (_: OptimisticLockingFailureException) {
            // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う
            Err(UpdateConflict)
        }

    /** 保存済み Row を、楽観ロック version を同梱した封筒つきドメイン集約へ写す。 */
    private fun BreedingRegistrationRow.toVersioned(): Versioned<BreedingRegistration> =
        Versioned(toDomain(), checkNotNull(version) { "保存済み行に version がありません: id=$id" })

    /**
     * 永続化モデルからドメイン集約を再構成する（検証・採番なし）。
     *
     * 登録番号は保存済みの検証済み値だが VO の生成口が `create`（`Result`）のみのため、ここで再構成して 失敗しない前提で取り出す（DB 由来の trusted
     * データ。infrastructure 層は例外送出が許容される）。 供用停止は 2 列が揃って non-null のときだけ復元する。
     */
    private fun BreedingRegistrationRow.toDomain(): BreedingRegistration =
        BreedingRegistration.reconstitute(
            id = BreedingRegistrationId(id),
            registrationNumber =
                BreedingRegistrationNumber.create(registrationNumber).getOrThrow {
                    IllegalStateException("永続化された繁殖登録番号がブランクです: id=$id")
                },
            registeredHorseId = BloodHorseId(registeredHorseId),
            role = BreedingRole.valueOf(breedingRole),
            retirement =
                retirementReason?.let { reason ->
                    BreedingRetirement(
                        RetirementReason.valueOf(reason),
                        checkNotNull(retirementOccurredOn) { "供用停止事由があるのに発生日が欠落しています: id=$id" },
                    )
                },
        )

    /**
     * ドメイン集約を永続化モデルへ写す。
     *
     * @param version 既存行を update するときは読み取り時点の version（[Versioned.version]）。新規 insert は null
     *   のまま（Spring Data JDBC が新規と判定する。ADR-0027 の落とし穴②③）
     */
    private fun BreedingRegistration.toRow(version: Long? = null): BreedingRegistrationRow =
        BreedingRegistrationRow(
            id = id.value,
            registrationNumber = registrationNumber.value,
            registeredHorseId = registeredHorseId.value,
            breedingRole = role.name,
            retirementReason = retirement?.reason?.name,
            retirementOccurredOn = retirement?.occurredOn,
            version = version,
        )
}
