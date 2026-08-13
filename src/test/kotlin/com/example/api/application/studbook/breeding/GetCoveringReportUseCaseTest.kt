package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.CoveringReportId
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

/**
 * 照会ユースケース [GetCoveringReportUseCase] の単体テスト（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りポート [CoveringReportQueries] を mockk でスタブし、ヒット時は [CoveringReportDetailView] を、不在時は
 * [CoveringReportNotFound] を返す分岐を検証する（testing.md: applicationService はポート境界をモックする）。
 */
class GetCoveringReportUseCaseTest {
    private val coveringReportQueries = mockk<CoveringReportQueries>()
    private val getCoveringReport = GetCoveringReportUseCase(coveringReportQueries)

    @Test
    fun `存在するIDなら対応するCoveringReportDetailViewをOkで返す`() {
        val id = generateId()
        val view =
            CoveringReportDetailView(
                id = id,
                stallionBreedingRegistrationId = generateId(),
                coveringYear = 2024,
                submittedOn = LocalDate.of(2024, 9, 30),
            )
        every { coveringReportQueries.findById(worldId, CoveringReportId(id)) } returns view

        val result = getCoveringReport(actor, GetCoveringReportQuery(id))

        assert(result.get() == view)
    }

    @Test
    fun `存在しないIDならCoveringReportNotFoundをErrで返す`() {
        val id = generateId()
        every { coveringReportQueries.findById(worldId, CoveringReportId(id)) } returns null

        val result = getCoveringReport(actor, GetCoveringReportQuery(id))

        assert(result.getError() == CoveringReportNotFound(id))
    }
}

/**
 * 読み取りモデル [CoveringReportDetailView] の導出値 `submittedLate` の単体テスト。
 *
 * 種付年 2024 の提出期限は当年 2024-09-30（`CoveringReportDeadline`）。集約側の導出
 * （`CoveringReport.submittedLate`）と同じ境界で判定できていることを確かめる。
 */
class CoveringReportDetailViewTest {
    private fun viewSubmittedOn(submittedOn: LocalDate): CoveringReportDetailView =
        CoveringReportDetailView(
            id = generateId(),
            stallionBreedingRegistrationId = generateId(),
            coveringYear = 2024,
            submittedOn = submittedOn,
        )

    @Test
    fun `期限日当日の提出は期限内`() {
        assert(!viewSubmittedOn(LocalDate.of(2024, 9, 30)).submittedLate)
    }

    @Test
    fun `期限の翌日の提出は期限超過`() {
        assert(viewSubmittedOn(LocalDate.of(2024, 10, 1)).submittedLate)
    }
}
