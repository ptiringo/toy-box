package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

class ListBreedingResultSummariesUseCaseTest {
    private val queries = mockk<BreedingResultSummaryQueries>()
    private val useCase = ListBreedingResultSummariesUseCase(queries)

    private val stallionId = BloodHorseId(UUID.fromString("11111111-1111-1111-1111-111111111111"))

    @Test
    fun `種牡馬IDで年次集計の一覧をポートから取得して返す`() {
        val rows =
            listOf(
                BreedingResultSummaryView.of(stallionId.value, 2023, 4, 3, 2),
                BreedingResultSummaryView.of(stallionId.value, 2024, 6, 5, 4),
            )
        every { queries.findByStallion(worldId, stallionId) } returns rows

        val result = useCase(actor, ListBreedingResultSummariesQuery(stallionId))

        assert(result == rows)
    }

    @Test
    fun `該当する成績が無ければ空リストを返す`() {
        every { queries.findByStallion(worldId, stallionId) } returns emptyList()

        val result = useCase(actor, ListBreedingResultSummariesQuery(stallionId))

        assert(result.isEmpty())
    }
}
