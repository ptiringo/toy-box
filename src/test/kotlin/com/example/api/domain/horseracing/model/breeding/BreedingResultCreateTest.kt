package com.example.api.domain.horseracing.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

/** [BreedingResult.create]（種付記録）のユニットテスト */
class BreedingResultCreateTest {
    private val coveringDate = LocalDate.of(2024, 4, 1)
    private val certificateNumber = CoveringCertificateNumber.create("C-2024-0001").unwrap()

    @Test
    fun `繁殖牝馬と種牡馬の登録なら種付が記録され種牡馬を ID で参照する繁殖成績が生成されること`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val stallionRegistration = BreedingFixture.stallionRegistration()

        val result =
            BreedingResult.create(
                    broodmareRegistration,
                    stallionRegistration,
                    coveringDate,
                    certificateNumber,
                )
                .unwrap()

        val covering = result.covering
        assert(covering != null)
        assert(result.breedingRegistrationId == broodmareRegistration.id)
        assert(covering?.stallionId == stallionRegistration.registeredHorseId)
        assert(covering?.coveringDate == coveringDate)
        assert(covering?.certificateNumber == certificateNumber)
        assert(result.outcome == null)
    }

    @Test
    fun `種付対象の登録ロールが繁殖牝馬でないと NotBroodmare を返すこと`() {
        val broodmareRegistration = BreedingFixture.stallionRegistration()
        val stallionRegistration = BreedingFixture.stallionRegistration()

        val result =
            BreedingResult.create(
                broodmareRegistration,
                stallionRegistration,
                coveringDate,
                certificateNumber,
            )

        assert(result.getError() == RecordCoveringError.NotBroodmare)
    }

    @Test
    fun `配合相手の登録ロールが種牡馬でないと NotStallion を返すこと`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val stallionRegistration = BreedingFixture.breedingRegistration()

        val result =
            BreedingResult.create(
                broodmareRegistration,
                stallionRegistration,
                coveringDate,
                certificateNumber,
            )

        assert(result.getError() == RecordCoveringError.NotStallion)
    }

    @Test
    fun `同一繁殖年に既存の成績があると AlreadyRecordedForYear を返し既存IDを伴うこと`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val stallionRegistration = BreedingFixture.stallionRegistration()
        val existingForYear =
            BreedingFixture.breedingResult(broodmareRegistration = broodmareRegistration)

        val result =
            BreedingResult.create(
                broodmareRegistration,
                stallionRegistration,
                coveringDate,
                certificateNumber,
                existingForYear,
            )

        assert(
            result.getError() ==
                RecordCoveringError.AlreadyRecordedForYear(Year.of(2024), existingForYear.id)
        )
    }
}
