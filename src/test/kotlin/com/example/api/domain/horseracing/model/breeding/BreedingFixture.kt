package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.unwrap
import java.time.LocalDate

/**
 * テスト用に繁殖コンテキストの集約・値オブジェクトを組み立てる Object Mother。
 *
 * [BreedingRegistration] / [BreedingResult] の生成口は本番ではドメインサービスに封じ込められている（`of` は
 * internal）。テストでは前提条件検証を経ずに任意の状態を用意したいため、internal 可視性がモジュール内（test も同一 モジュール）から見えることを利用してここで直接組み立てる。
 */
object BreedingFixture {
    /** 既定値（ロールは繁殖牝馬）を持つ [BreedingRegistration] を生成する。必要な属性のみ上書きする。 */
    fun breedingRegistration(
        registeredHorseId: BloodHorseId = BloodHorseId(generateId()),
        role: BreedingRole = BreedingRole.BROODMARE,
    ): BreedingRegistration =
        BreedingRegistration.of(
            registrationNumber = BreedingRegistrationNumber.create("B-2024-0001").unwrap(),
            registeredHorseId = registeredHorseId,
            role = role,
        )

    /** 既定値を持つ、ロールが種牡馬の [BreedingRegistration] を生成する。必要な属性のみ上書きする。 */
    fun stallionRegistration(
        registeredHorseId: BloodHorseId = BloodHorseId(generateId())
    ): BreedingRegistration =
        breedingRegistration(registeredHorseId = registeredHorseId, role = BreedingRole.STALLION)

    /** 既定値を持つ [Covering] を生成する。必要な属性のみ上書きする。 */
    fun covering(
        stallionId: BloodHorseId = BloodHorseId(generateId()),
        coveringDate: LocalDate = LocalDate.of(2024, 4, 1),
    ): Covering =
        Covering(
            stallionId = stallionId,
            coveringDate = coveringDate,
            certificateNumber = CoveringCertificateNumber.create("C-2024-0001").unwrap(),
        )

    /** 既定値を持つ、分娩結果未報告の [BreedingResult] を生成する。必要な属性のみ上書きする。 */
    fun breedingResult(
        breedingRegistrationId: BreedingRegistrationId = breedingRegistration().id,
        covering: Covering = covering(),
    ): BreedingResult =
        BreedingResult.of(breedingRegistrationId = breedingRegistrationId, covering = covering)
}
