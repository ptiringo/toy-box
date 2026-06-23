package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingFixture
import com.example.api.domain.horseracing.model.breeding.CoveringCertificateNumber
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

/** [recordCovering] ドメインサービスのユニットテスト */
class RecordCoveringTest {
    private val coveringDate = LocalDate.of(2024, 4, 1)
    private val certificateNumber = CoveringCertificateNumber.create("C-2024-0001").unwrap()

    @Test
    fun `同年の既存成績が無ければ種付が記録され分娩結果未報告の繁殖成績が生成されること`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val stallionRegistration = BreedingFixture.stallionRegistration()

        val result =
            recordCovering(
                    broodmareRegistration,
                    stallionRegistration,
                    coveringDate,
                    certificateNumber,
                    existingForYear = null,
                )
                .unwrap()

        val covering = result.covering
        assert(covering != null)
        assert(result.breedingRegistrationId == broodmareRegistration.id)
        assert(covering?.stallionId == stallionRegistration.registeredHorseId)
        assert(result.outcome == null)
    }

    @Test
    fun `同一繁殖牝馬の同一繁殖年に既存成績があると AlreadyRecordedForYear を返し既存IDを伴うこと`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val stallionRegistration = BreedingFixture.stallionRegistration()
        val existingForYear =
            BreedingFixture.breedingResult(broodmareRegistration = broodmareRegistration)

        val result =
            recordCovering(
                broodmareRegistration,
                stallionRegistration,
                coveringDate,
                certificateNumber,
                existingForYear = existingForYear,
            )

        assert(
            result.getError() ==
                RecordCoveringError.AlreadyRecordedForYear(Year.of(2024), existingForYear.id)
        )
    }

    @Test
    fun `種付対象の登録ロールが繁殖牝馬でないとファクトリの NotBroodmare が伝播すること`() {
        val notBroodmareRegistration = BreedingFixture.stallionRegistration()
        val stallionRegistration = BreedingFixture.stallionRegistration()

        val result =
            recordCovering(
                notBroodmareRegistration,
                stallionRegistration,
                coveringDate,
                certificateNumber,
                existingForYear = null,
            )

        assert(result.getError() == RecordCoveringError.NotBroodmare)
    }

    @Test
    fun `配合相手の登録ロールが種牡馬でないとファクトリの NotStallion が伝播すること`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val notStallionRegistration = BreedingFixture.breedingRegistration()

        val result =
            recordCovering(
                broodmareRegistration,
                notStallionRegistration,
                coveringDate,
                certificateNumber,
                existingForYear = null,
            )

        assert(result.getError() == RecordCoveringError.NotStallion)
    }
}
