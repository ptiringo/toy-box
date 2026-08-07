package com.example.api.application.studbook.inspection

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspectionId
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

/**
 * 照会ユースケース [GetHorseInspectionUseCase] の単体テスト（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りポート [HorseInspectionQueries] を mockk でスタブし、ヒット時は [HorseInspectionView] を、不在時は
 * [HorseInspectionNotFound] を返す分岐を検証する（testing.md: applicationService はポート境界をモックする）。
 */
class GetHorseInspectionUseCaseTest {
    private val horseInspectionQueries = mockk<HorseInspectionQueries>()
    private val getHorseInspection = GetHorseInspectionUseCase(horseInspectionQueries)

    @Test
    fun `存在するIDなら対応するHorseInspectionViewをOkで返す`() {
        val id = generateId()
        val view =
            HorseInspectionView(
                id = id,
                microchipNumber = "392140000000001",
                parentage = ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT),
                features = null,
            )
        every { horseInspectionQueries.findById(worldId, HorseInspectionId(id)) } returns view

        val result = getHorseInspection(actor, GetHorseInspectionQuery(id))

        assert(result.get() == view)
    }

    @Test
    fun `存在しないIDならHorseInspectionNotFoundをErrで返す`() {
        val id = generateId()
        every { horseInspectionQueries.findById(worldId, HorseInspectionId(id)) } returns null

        val result = getHorseInspection(actor, GetHorseInspectionQuery(id))

        assert(result.getError() == HorseInspectionNotFound(id))
    }
}
