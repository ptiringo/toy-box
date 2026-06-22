package com.example.api.domain.horseracing.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.Year
import org.junit.jupiter.api.Test

/** [BreedingResult.createUncovered]（種付せず＝種付しなかった年次成績の記録）のユニットテスト */
class BreedingResultCreateUncoveredTest {
    @Test
    fun `繁殖牝馬の登録なら種付を伴わない NotCovered の年次成績が生成されること`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()

        val result = BreedingResult.createUncovered(broodmareRegistration, Year.of(2024)).unwrap()

        assert(result.breedingRegistrationId == broodmareRegistration.id)
        assert(result.breedingYear == Year.of(2024))
        assert(result.covering == null)
        assert(result.outcome == FoalingOutcome.NotCovered)
    }

    @Test
    fun `登録ロールが繁殖牝馬でないと NotBroodmareForUncovered を返すこと`() {
        val stallionRegistration = BreedingFixture.stallionRegistration()

        val result = BreedingResult.createUncovered(stallionRegistration, Year.of(2024))

        assert(result.getError() == NotBroodmareForUncovered(stallionRegistration))
    }
}
