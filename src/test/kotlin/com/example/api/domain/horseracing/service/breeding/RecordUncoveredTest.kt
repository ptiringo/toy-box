package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.BreedingResultRepository
import com.example.api.domain.horseracing.model.breeding.FoalingOutcome
import com.example.api.domain.horseracing.model.breeding.RecordUncoveredError
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import java.time.Year
import org.junit.jupiter.api.Test

/** [recordUncovered] ドメインサービスのユニットテスト */
class RecordUncoveredTest {
    private val breedingYear = Year.of(2024)

    @Test
    fun `同年の既存成績が無ければ種付せずの終端な繁殖成績が生成されること`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val repository =
            mockk<BreedingResultRepository> {
                every { findByBreedingRegistrationIdAndBreedingYear(any(), any()) } returns null
            }

        val result = recordUncovered(broodmareRegistration, breedingYear, repository).unwrap()

        assert(result.breedingRegistrationId == broodmareRegistration.id)
        assert(result.breedingYear == breedingYear)
        assert(result.covering == null)
        assert(result.outcome == FoalingOutcome.NotCovered)
    }

    @Test
    fun `同一繁殖牝馬の同一繁殖年に既存成績があると AlreadyRecordedForYear を返し既存IDを伴うこと`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val existing = BreedingFixture.breedingResult(broodmareRegistration = broodmareRegistration)
        val repository =
            mockk<BreedingResultRepository> {
                every {
                    findByBreedingRegistrationIdAndBreedingYear(
                        broodmareRegistration.id,
                        breedingYear,
                    )
                } returns existing
            }

        val result = recordUncovered(broodmareRegistration, breedingYear, repository)

        assert(
            result.getError() ==
                RecordUncoveredError.AlreadyRecordedForYear(breedingYear, existing.id)
        )
    }

    @Test
    fun `既に種付記録がある年への種付せずも AlreadyRecordedForYear で弾かれること`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val existingCovered =
            BreedingFixture.breedingResult(broodmareRegistration = broodmareRegistration)
        val repository =
            mockk<BreedingResultRepository> {
                every { findByBreedingRegistrationIdAndBreedingYear(any(), any()) } returns
                    existingCovered
            }

        val result = recordUncovered(broodmareRegistration, breedingYear, repository)

        assert(
            result.getError() ==
                RecordUncoveredError.AlreadyRecordedForYear(breedingYear, existingCovered.id)
        )
    }

    @Test
    fun `記録対象の登録ロールが繁殖牝馬でないとファクトリの NotBroodmare が伝播すること`() {
        val notBroodmareRegistration = BreedingFixture.stallionRegistration()
        val repository =
            mockk<BreedingResultRepository> {
                every { findByBreedingRegistrationIdAndBreedingYear(any(), any()) } returns null
            }

        val result = recordUncovered(notBroodmareRegistration, breedingYear, repository)

        assert(result.getError() == RecordUncoveredError.NotBroodmare)
    }
}
