package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.unwrap
import java.time.LocalDate

/**
 * テスト用に繁殖コンテキストの集約・値オブジェクトを組み立てる Object Mother。
 *
 * [BreedingRegistration] / [BreedingResult] の生成ファクトリ（`create`）は前提条件（繁殖牝馬が雌・種牡馬が雄）を
 * 自己検証する。ここでは検証を通すため、適切な性の馬を [BloodHorseFixture] で用意してから組み立てる（`unwrap` で成功を取り出す）。
 */
object BreedingFixture {
    /** 既定値を持つ [BreedingRegistration] を生成する。繁殖牝馬は必要に応じて上書きする。 */
    fun breedingRegistration(
        broodmare: BloodHorse = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
    ): BreedingRegistration =
        BreedingRegistration.create(
                registrationNumber = BreedingRegistrationNumber.create("B-2024-0001").unwrap(),
                broodmare = broodmare,
            )
            .unwrap()

    /** 既定値を持つ、分娩結果未報告の [BreedingResult] を生成する。必要な属性のみ上書きする。 */
    fun breedingResult(
        breedingRegistration: BreedingRegistration = breedingRegistration(),
        stallion: BloodHorse = BloodHorseFixture.bloodHorse(sex = Sex.MALE),
        coveringDate: LocalDate = LocalDate.of(2024, 4, 1),
        certificateNumber: CoveringCertificateNumber =
            CoveringCertificateNumber.create("C-2024-0001").unwrap(),
    ): BreedingResult =
        BreedingResult.create(breedingRegistration, stallion, coveringDate, certificateNumber)
            .unwrap()
}
