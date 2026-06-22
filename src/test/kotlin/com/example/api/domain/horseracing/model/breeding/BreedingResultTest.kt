package com.example.api.domain.horseracing.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

/** BreedingResult 集約のユニットテスト */
class BreedingResultTest {
    @Test
    fun `種付した年の繁殖年は種付日の年と一致すること`() {
        val result = BreedingFixture.breedingResult(coveringDate = LocalDate.of(2024, 4, 1))

        assert(result.breedingYear == Year.of(2024))
    }

    @Test
    fun `生成直後は分娩結果が未報告であること`() {
        val result = BreedingFixture.breedingResult()

        assert(result.outcome == null)
    }

    @Test
    fun `分娩結果を報告すると outcome を持つ新インスタンスが返り同一性が引き継がれること`() {
        val result = BreedingFixture.breedingResult()
        val outcome = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))

        val reported = result.recordFoaling(outcome).unwrap()

        assert(reported.outcome == outcome)
        assert(reported.id == result.id)
        // 元のインスタンスは不変
        assert(result.outcome == null)
    }

    @Test
    fun `産駒なしの帰結も分娩結果として報告できること`() {
        val result = BreedingFixture.breedingResult()

        val reported = result.recordFoaling(FoalingOutcome.NotConceived).unwrap()

        assert(reported.outcome == FoalingOutcome.NotConceived)
    }

    @Test
    fun `既に報告済みの成績へ再報告すると FoalingAlreadyRecorded を返すこと`() {
        val first = FoalingOutcome.LiveFoal(LocalDate.of(2025, 3, 20))
        val reported = BreedingFixture.breedingResult().recordFoaling(first).unwrap()

        val error = reported.recordFoaling(FoalingOutcome.NotConceived).getError()

        assert(error == FoalingAlreadyRecorded(first))
    }

    @Test
    fun `種付せずの成績は covering を持たず NotCovered で終端すること`() {
        val result = BreedingFixture.uncoveredBreedingResult(breedingYear = Year.of(2024))

        assert(result.covering == null)
        assert(result.breedingYear == Year.of(2024))
        assert(result.outcome == FoalingOutcome.NotCovered)
    }

    @Test
    fun `種付せずの成績へ分娩結果を報告すると FoalingAlreadyRecorded を返すこと`() {
        val result = BreedingFixture.uncoveredBreedingResult()

        val error = result.recordFoaling(FoalingOutcome.NotConceived).getError()

        assert(error == FoalingAlreadyRecorded(FoalingOutcome.NotCovered))
    }

    @Test
    fun `NotCovered を分娩結果として報告しようとすると拒否されること`() {
        val result = BreedingFixture.breedingResult()

        val error =
            runCatching { result.recordFoaling(FoalingOutcome.NotCovered) }.exceptionOrNull()

        assert(error is IllegalArgumentException)
    }
}
