package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

/**
 * 照会ユースケース [GetBreedingResultUseCase] の単体テスト（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りポート [BreedingResultQueries] を mockk でスタブし、ヒット時は [BreedingResultDetailView] を、不在時は
 * [BreedingResultNotFound] を返す分岐を検証する（testing.md: applicationService はポート境界をモックする）。
 */
class GetBreedingResultUseCaseTest {
    private val breedingResultQueries = mockk<BreedingResultQueries>()
    private val getBreedingResult = GetBreedingResultUseCase(breedingResultQueries)

    /** 種付済み・分娩結果未報告の年次成績ビュー。提出も未了。 */
    private fun coveredView(id: UUID): BreedingResultDetailView =
        BreedingResultDetailView(
            id = id,
            breedingRegistrationId = generateId(),
            breedingYear = 2024,
            stallionId = generateId(),
            coveringDate = LocalDate.of(2024, 4, 1),
            coveringPlace = "北海道",
            certificateNumber = "C-2024-0001",
            outcome = null,
            reportSubmittedOn = null,
        )

    @Test
    fun `存在するIDなら対応するBreedingResultDetailViewをOkで返す`() {
        val id = generateId()
        val view = coveredView(id)
        every { breedingResultQueries.findById(worldId, BreedingResultId(id)) } returns view

        val result = getBreedingResult(actor, GetBreedingResultQuery(id))

        assert(result.get() == view)
    }

    @Test
    fun `存在しないIDならBreedingResultNotFoundをErrで返す`() {
        val id = generateId()
        every { breedingResultQueries.findById(worldId, BreedingResultId(id)) } returns null

        val result = getBreedingResult(actor, GetBreedingResultQuery(id))

        assert(result.getError() == BreedingResultNotFound(id))
    }
}

/**
 * 読み取りモデル [BreedingResultDetailView] の導出値 `reportSubmittedLate` の単体テスト。
 *
 * 繁殖年 2024 の提出期限は翌年 2025-05-31（`BreedingReportDeadline`）。集約側の導出
 * （`BreedingResult.reportSubmittedLate`）と同じ境界で判定できていることを確かめる。
 */
class BreedingResultDetailViewTest {
    private fun viewSubmittedOn(submittedOn: LocalDate?): BreedingResultDetailView =
        BreedingResultDetailView(
            id = generateId(),
            breedingRegistrationId = generateId(),
            breedingYear = 2024,
            stallionId = generateId(),
            coveringDate = LocalDate.of(2024, 4, 1),
            coveringPlace = "北海道",
            certificateNumber = "C-2024-0001",
            outcome = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20)),
            reportSubmittedOn = submittedOn,
        )

    @Test
    fun `未提出なら期限超過は null`() {
        assert(viewSubmittedOn(null).reportSubmittedLate == null)
    }

    @Test
    fun `期限日当日の提出は期限内`() {
        assert(viewSubmittedOn(LocalDate.of(2025, 5, 31)).reportSubmittedLate == false)
    }

    @Test
    fun `期限の翌日の提出は期限超過`() {
        assert(viewSubmittedOn(LocalDate.of(2025, 6, 1)).reportSubmittedLate == true)
    }
}
