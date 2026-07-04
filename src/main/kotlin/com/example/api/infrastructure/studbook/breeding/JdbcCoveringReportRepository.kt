package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.CoveringReport
import com.example.api.domain.studbook.model.breeding.CoveringReportId
import com.example.api.domain.studbook.model.breeding.CoveringReportRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.Year
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository

/**
 * ドメインポート [CoveringReportRepository] の唯一の実装。Spring Data JDBC で永続化する（ADR-0027 / ADR-0030）。
 *
 * ドメイン集約 [CoveringReport] と永続化モデル [CoveringReportRow] を手書きマッパーで相互変換し、CRUD は
 * [CoveringReportSpringDataRepository] へ委譲する。value class ID・`java.time.Year` の橋渡しも本マッパーが
 * 担う（永続化モデルを分離した帰結。ADR-0027）。
 */
@Repository
class JdbcCoveringReportRepository(private val rows: CoveringReportSpringDataRepository) :
    CoveringReportRepository {

    override fun findById(id: CoveringReportId): CoveringReport? =
        rows.findById(id.value).map { it.toDomain() }.orElse(null)

    override fun findByStallionRegistrationIdAndCoveringYear(
        stallionRegistrationId: BreedingRegistrationId,
        coveringYear: Year,
    ): CoveringReport? =
        rows
            .findByStallionBreedingRegistrationIdAndCoveringYear(
                stallionRegistrationId.value,
                coveringYear.value,
            )
            ?.toDomain()

    override fun save(coveringReport: CoveringReport): Result<CoveringReport, UpdateConflict> =
        try {
            Ok(rows.save(coveringReport.toRow()).toDomain())
        } catch (_: OptimisticLockingFailureException) {
            // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う
            Err(UpdateConflict)
        }

    /** 永続化モデルからドメイン集約を再構成する（検証・採番なし）。 */
    private fun CoveringReportRow.toDomain(): CoveringReport =
        CoveringReport.reconstitute(
            id = CoveringReportId(id),
            stallionRegistrationId = BreedingRegistrationId(stallionBreedingRegistrationId),
            coveringYear = Year.of(coveringYear),
            submittedOn = submittedOn,
            version = version,
        )

    /**
     * ドメイン集約を永続化モデルへ写す。
     *
     * version は集約が保持する値をそのまま写す（null なら Spring Data JDBC が新規と判定して insert、非 null なら楽観ロック付き
     * update。ADR-0027 の落とし穴②③）。
     */
    private fun CoveringReport.toRow(): CoveringReportRow =
        CoveringReportRow(
            id = id.value,
            stallionBreedingRegistrationId = stallionRegistrationId.value,
            coveringYear = coveringYear.value,
            submittedOn = submittedOn,
            version = version,
        )
}
