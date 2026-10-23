package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

/** [BreedingResult.createOverseas]（海外種付の記録）のユニットテスト */
class BreedingResultCreateOverseasTest {
    private val sire = BloodHorseFixture.foreignSire()
    private val coveringDate = LocalDate.of(1999, 4, 10)
    private val coveringCountry = OriginCountry.create("アイルランド").unwrap()
    private val proofNumber = ForeignCoveringProofNumber.create("IE-1999-0004").unwrap()

    @Test
    fun `繁殖牝馬なら海外種付の年次成績を起こす`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()

        val result =
            BreedingResult.createOverseas(
                    broodmareRegistration,
                    sire,
                    coveringDate,
                    coveringCountry,
                    proofNumber,
                )
                .unwrap()

        val covering = result.covering as? Covering.Overseas
        assert(covering != null)
        assert(covering?.sire == sire)
        assert(covering?.coveringDate == coveringDate)
        assert(covering?.coveringCountry == coveringCountry)
        assert(covering?.proofNumber == proofNumber)
        assert(result.breedingRegistrationId == broodmareRegistration.id)
        assert(result.breedingYear == Year.of(1999))
        assert(result.outcome == null)
    }

    @Test
    fun `種牡馬の登録だと NotBroodmare を返す`() {
        val result =
            BreedingResult.createOverseas(
                BreedingFixture.stallionRegistration(),
                sire,
                coveringDate,
                coveringCountry,
                proofNumber,
            )

        assert(result.getError() == RecordOverseasCoveringError.NotBroodmare)
    }
}
