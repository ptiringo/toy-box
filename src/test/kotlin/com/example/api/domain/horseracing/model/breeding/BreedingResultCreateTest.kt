package com.example.api.domain.horseracing.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** [BreedingResult.create]（種付記録）のユニットテスト */
class BreedingResultCreateTest {
    private val coveringDate = LocalDate.of(2024, 4, 1)
    private val certificateNumber = CoveringCertificateNumber.create("C-2024-0001").unwrap()
    private val coveringPlace = BreedingRegion.create("北海道").unwrap()
    private val studCertificate =
        BreedingFixture.studCertificate(validRegions = setOf(coveringPlace))

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
                    studCertificate,
                    coveringPlace,
                )
                .unwrap()

        val covering = result.covering
        assert(covering != null)
        assert(result.breedingRegistrationId == broodmareRegistration.id)
        assert(covering?.stallionId == stallionRegistration.registeredHorseId)
        assert(covering?.coveringDate == coveringDate)
        assert(covering?.coveringPlace == coveringPlace)
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
                studCertificate,
                coveringPlace,
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
                studCertificate,
                coveringPlace,
            )

        assert(result.getError() == RecordCoveringError.NotStallion)
    }

    @Test
    fun `種畜証明書を渡すと有効区域内かつ有効期間内の種付が記録され場所を保持すること`() {
        val place = BreedingRegion.create("北海道").unwrap()
        val studCertificate = BreedingFixture.studCertificate(validRegions = setOf(place))

        val result =
            BreedingResult.create(
                    BreedingFixture.breedingRegistration(),
                    BreedingFixture.stallionRegistration(),
                    coveringDate,
                    certificateNumber,
                    studCertificate,
                    place,
                )
                .unwrap()

        assert(result.covering?.coveringPlace == place)
    }

    @Test
    fun `種付日が種畜証明書の有効期間外だと InvalidCovering(OutsideValidPeriod) を返すこと`() {
        val place = BreedingRegion.create("北海道").unwrap()
        val period =
            ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)).unwrap()
        val studCertificate =
            BreedingFixture.studCertificate(validRegions = setOf(place), validPeriod = period)

        val result =
            BreedingResult.create(
                BreedingFixture.breedingRegistration(),
                BreedingFixture.stallionRegistration(),
                coveringDate, // 2024-04-01 は 3/31 までの有効期間外
                certificateNumber,
                studCertificate,
                place,
            )

        val error = result.getError()
        assert(error is RecordCoveringError.InvalidCovering)
        assert(
            (error as RecordCoveringError.InvalidCovering).cause
                is CoveringValidityError.OutsideValidPeriod
        )
    }

    @Test
    fun `種付場所が種畜証明書の有効区域外だと InvalidCovering(OutsideValidRegion) を返すこと`() {
        val validRegion = BreedingRegion.create("北海道").unwrap()
        val otherPlace = BreedingRegion.create("青森").unwrap()
        val studCertificate = BreedingFixture.studCertificate(validRegions = setOf(validRegion))

        val result =
            BreedingResult.create(
                BreedingFixture.breedingRegistration(),
                BreedingFixture.stallionRegistration(),
                coveringDate,
                certificateNumber,
                studCertificate,
                otherPlace,
            )

        val error = result.getError()
        assert(error is RecordCoveringError.InvalidCovering)
        assert(
            (error as RecordCoveringError.InvalidCovering).cause
                is CoveringValidityError.OutsideValidRegion
        )
    }
}
