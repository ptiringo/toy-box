package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.infrastructure.shared.lockRowIfVersionMatches
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * ドメインポート [BreedingRegistrationRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [BreedingRegistration] と永続化モデル [BreedingRegistrationRow] を手書きマッパーで相互変換し、CRUD は
 * [BreedingRegistrationSpringDataRepository] へ委譲する。value class の各種 ID（`BreedingRegistrationId` /
 * `BloodHorseId`）↔ DB `uuid` 列、enum（`BreedingRole` / `RetirementReason`）↔ 文字列列、nullable な供用停止
 * （`BreedingRetirement`）↔ 2 列のフラット化も本マッパーが担う（永続化モデルを分離した帰結。ADR-0027）。
 *
 * 永続化実装は JDBC 一本に統一し、datasource は実行環境が外部供給する（本番 = Prisma Postgres の env 注入、ローカル = docker-compose の
 * PostgreSQL。H2 全面脱却・#451）ため、InMemory 実装・プロファイル切替は持たない（ADR-0030）。
 */
@Repository
class JdbcBreedingRegistrationRepository(
    private val rows: BreedingRegistrationSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : BreedingRegistrationRepository {

    override fun findById(worldId: WorldId, id: BreedingRegistrationId): BreedingRegistration? =
        rows.findByWorldIdAndId(worldId.value, id.value)?.toDomain()

    /**
     * 版が一致するときだけ更新する（競合は例外にせず [UpdateConflict] で返す。#867）。
     *
     * insert（version が null）はロック不要。update のときだけ [lockRowIfVersionMatches] で行をロックして版を
     * 突き合わせ、一致した場合にのみ `save` を通す（例外に頼れない理由はヘルパーの KDoc）。
     */
    override fun save(
        worldId: WorldId,
        breedingRegistration: BreedingRegistration,
    ): Result<BreedingRegistration, UpdateConflict> {
        val version = breedingRegistration.version
        val id = breedingRegistration.id.value
        // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う
        if (version != null && !jdbcClient.lockRowIfVersionMatches(TABLE, id, version)) {
            return Err(UpdateConflict)
        }
        return Ok(rows.save(breedingRegistration.toRow(worldId)).toDomain())
    }

    override fun existsByRegistrationNumber(
        worldId: WorldId,
        number: BreedingRegistrationNumber,
    ): Boolean = rows.existsByWorldIdAndRegistrationNumber(worldId.value, number.value)

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
            version = version,
        )

    /**
     * ドメイン集約を永続化モデルへ写す。
     *
     * version は集約が保持する値をそのまま写す（null なら Spring Data JDBC が新規と判定して insert、非 null なら 楽観ロック付き
     * update。ADR-0027 の落とし穴②③）。
     */
    private fun BreedingRegistration.toRow(worldId: WorldId): BreedingRegistrationRow =
        BreedingRegistrationRow(
            id = id.value,
            worldId = worldId.value,
            registrationNumber = registrationNumber.value,
            registeredHorseId = registeredHorseId.value,
            breedingRole = role.name,
            retirementReason = retirement?.reason?.name,
            retirementOccurredOn = retirement?.occurredOn,
            version = version,
        )

    private companion object {
        /** 楽観ロックの行ロックを掛ける対象（[lockRowIfVersionMatches] に渡す完全修飾名）。 */
        const val TABLE = "studbook.breeding_registration"
    }
}
