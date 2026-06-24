package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year

/** テスト用の有効区域（種付場所）。 */
private val DEFAULT_REGION = BreedingRegion.create("北海道").unwrap()

/**
 * テスト用に繁殖コンテキストの集約・値オブジェクトを組み立てる Object Mother。
 *
 * 繁殖登録は雄雌共通の単一の登録で、性からロール（種牡馬／繁殖牝馬）が定まる。[breedingRegistration] は繁殖牝馬、 [stallionRegistration]
 * は種牡馬のロールを持つ登録を組む。[BreedingResult.create] は両登録のロールを自己検証するため、 適切な登録を渡して `unwrap` で成功を取り出す。
 */
object BreedingFixture {
    /** ロールが繁殖牝馬の [BreedingRegistration] を生成する。繁殖牝馬は必要に応じて上書きする。 */
    fun breedingRegistration(
        broodmare: BloodHorse = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
    ): BreedingRegistration =
        BreedingRegistration.create(
            registrationNumber = BreedingRegistrationNumber.create("B-2024-0001").unwrap(),
            horse = broodmare,
        )

    /** ロールが種牡馬の [BreedingRegistration] を生成する。種牡馬は必要に応じて上書きする。 */
    fun stallionRegistration(
        stallion: BloodHorse = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
    ): BreedingRegistration =
        BreedingRegistration.create(
            registrationNumber = BreedingRegistrationNumber.create("B-2024-0002").unwrap(),
            horse = stallion,
        )

    /**
     * 既定で [coveringDate]（2024-04-01）・[validRegions]（北海道）を覆う種畜証明書を生成する。
     *
     * 有効性検証（有効区域・有効期間）のテストで、種付日・場所を覆う／外れる証明書を組むのに使う。
     */
    fun studCertificate(
        number: StudCertificateNumber = StudCertificateNumber.create("S-2024-0001").unwrap(),
        validRegions: Set<BreedingRegion> = setOf(DEFAULT_REGION),
        validPeriod: ValidityPeriod =
            ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).unwrap(),
    ): StudCertificate = StudCertificate.create(number, validRegions, validPeriod).unwrap()

    /** 既定値を持つ、分娩結果未報告の [BreedingResult] を生成する。必要な属性のみ上書きする。 */
    fun breedingResult(
        broodmareRegistration: BreedingRegistration = breedingRegistration(),
        stallionRegistration: BreedingRegistration = stallionRegistration(),
        coveringDate: LocalDate = LocalDate.of(2024, 4, 1),
        certificateNumber: CoveringCertificateNumber =
            CoveringCertificateNumber.create("C-2024-0001").unwrap(),
        studCertificate: StudCertificate = studCertificate(),
        coveringPlace: BreedingRegion = DEFAULT_REGION,
    ): BreedingResult =
        BreedingResult.create(
                broodmareRegistration,
                stallionRegistration,
                coveringDate,
                certificateNumber,
                studCertificate,
                coveringPlace,
            )
            .unwrap()

    /** 既定値を持つ、種付せず（種付しなかった年次成績）の [BreedingResult] を生成する。 */
    fun uncoveredBreedingResult(
        broodmareRegistration: BreedingRegistration = breedingRegistration(),
        breedingYear: Year = Year.of(2024),
    ): BreedingResult = BreedingResult.createUncovered(broodmareRegistration, breedingYear).unwrap()
}
