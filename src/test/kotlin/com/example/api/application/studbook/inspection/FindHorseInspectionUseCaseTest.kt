package com.example.api.application.studbook.inspection

import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspectionId
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * 照会ユースケース [FindHorseInspectionUseCase] の単体テスト（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りポート [HorseInspectionQueries] を mockk でスタブし、ヒット時は [HorseInspectionView] を、不在時は
 * [HorseInspectionNotFound] を返す分岐を検証する（testing.md: applicationService はポート境界をモックする）。
 */
class FindHorseInspectionUseCaseTest {
    private val horseInspectionQueries = mockk<HorseInspectionQueries>()
    private val findHorseInspection = FindHorseInspectionUseCase(horseInspectionQueries)

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
        every { horseInspectionQueries.findById(HorseInspectionId(id)) } returns view

        val result = findHorseInspection(FindHorseInspectionQuery(id))

        assert(result.get() == view)
    }

    @Test
    fun `存在しないIDならHorseInspectionNotFoundをErrで返す`() {
        val id = generateId()
        every { horseInspectionQueries.findById(HorseInspectionId(id)) } returns null

        val result = findHorseInspection(FindHorseInspectionQuery(id))

        assert(result.getError() == HorseInspectionNotFound(id))
    }
}
