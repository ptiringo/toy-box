package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.unwrap
import java.time.LocalDate

/**
 * テスト用に [BloodHorse] / [StudBookEntry] を組み立てる Object Mother。
 *
 * [BloodHorse] の生成口は本番では registerInStudBook に封じ込められている（[BloodHorse.of] は internal）。
 * テストでは前提条件検証を経ずに任意の馬を用意したい（例: registerInStudBook へ渡す父・母）。 internal 可視性は モジュール内（test
 * も同一モジュール）から見えるため、ここで [BloodHorse.of] を直接呼んで生成する。
 */
object BloodHorseFixture {
    /** 既定値を持つ [StudBookEntry] を生成する。必要な属性のみ上書きする。 */
    fun studBookEntry(
        sex: Sex = Sex.MALE,
        coatColor: CoatColor = CoatColor.BAY,
        breedType: BreedType = BreedType.THOROUGHBRED,
        dnaParentage: DnaParentageResult = DnaParentageResult.CONSISTENT,
    ): StudBookEntry =
        StudBookEntry(
            sex = sex,
            coatColor = coatColor,
            breedType = breedType,
            dateOfBirth = DateOfBirth(LocalDate.of(2023, 3, 15)),
            breeder = Breeder.create("ノーザンファーム").unwrap(),
            microchipNumber = MicrochipNumber.create("392140000000001").unwrap(),
            dnaParentage = dnaParentage,
        )

    /** 既定値を持つ [FoalIdentity] を生成する。必要な属性のみ上書きする。 */
    fun foalIdentity(
        sex: Sex = Sex.MALE,
        coatColor: CoatColor = CoatColor.BAY,
        breedType: BreedType = BreedType.THOROUGHBRED,
        dnaParentage: DnaParentageResult = DnaParentageResult.CONSISTENT,
    ): FoalIdentity =
        FoalIdentity(
            sex = sex,
            coatColor = coatColor,
            breedType = breedType,
            breeder = Breeder.create("ノーザンファーム").unwrap(),
            microchipNumber = MicrochipNumber.create("392140000000001").unwrap(),
            dnaParentage = dnaParentage,
        )

    /** 既定値を持つ [BloodHorse] を生成する。性・品種など必要な属性のみ上書きする。 */
    fun bloodHorse(sex: Sex = Sex.MALE, breedType: BreedType = BreedType.THOROUGHBRED): BloodHorse =
        BloodHorse.of(
            entry = studBookEntry(sex = sex, breedType = breedType),
            sireId = BloodHorseId(generateId()),
            damId = BloodHorseId(generateId()),
            registrationNumber = PedigreeRegistrationNumber.create("2023104567").unwrap(),
        )
}
