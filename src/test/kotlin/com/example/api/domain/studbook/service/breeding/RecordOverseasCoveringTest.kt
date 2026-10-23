package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.Covering
import com.example.api.domain.studbook.model.breeding.ForeignCoveringProofNumber
import com.example.api.domain.studbook.model.breeding.RecordOverseasCoveringError
import com.example.api.domain.studbook.model.breeding.ValidityPeriod
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

/** [recordOverseasCovering] ドメインサービスのユニットテスト */
class RecordOverseasCoveringTest {
    private val worldId = WorldId(generateId())
    private val sire = BloodHorseFixture.foreignSire()
    private val coveringDate = LocalDate.of(1999, 4, 10)
    private val coveringCountry = OriginCountry.create("アイルランド").unwrap()
    private val proofNumber = ForeignCoveringProofNumber.create("IE-1999-0004").unwrap()

    @Test
    fun `重複が無ければ海外種付の成績を返す`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val repository =
            mockk<BreedingResultRepository> {
                every { findByBreedingRegistrationIdAndBreedingYear(worldId, any(), any()) } returns
                    null
            }

        val result =
            recordOverseasCovering(
                    worldId,
                    broodmareRegistration,
                    sire,
                    coveringDate,
                    coveringCountry,
                    proofNumber,
                    repository,
                )
                .unwrap()

        assert(result.covering is Covering.Overseas)
        assert(result.breedingRegistrationId == broodmareRegistration.id)
    }

    @Test
    fun `同じ繁殖牝馬・同じ年に国内種付の成績があると AlreadyRecordedForYear を返す`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()
        val existing =
            BreedingFixture.breedingResult(
                broodmareRegistration = broodmareRegistration,
                coveringDate = LocalDate.of(1999, 4, 1),
                studCertificate =
                    BreedingFixture.studCertificate(
                        validPeriod =
                            ValidityPeriod.create(
                                    LocalDate.of(1999, 1, 1),
                                    LocalDate.of(1999, 12, 31),
                                )
                                .unwrap()
                    ),
            )
        val repository =
            mockk<BreedingResultRepository> {
                every {
                    findByBreedingRegistrationIdAndBreedingYear(
                        worldId,
                        broodmareRegistration.id,
                        Year.of(1999),
                    )
                } returns existing
            }

        val result =
            recordOverseasCovering(
                worldId,
                broodmareRegistration,
                sire,
                coveringDate,
                coveringCountry,
                proofNumber,
                repository,
            )

        assert(
            result.getError() ==
                RecordOverseasCoveringError.AlreadyRecordedForYear(Year.of(1999), existing.id)
        )
    }
}
