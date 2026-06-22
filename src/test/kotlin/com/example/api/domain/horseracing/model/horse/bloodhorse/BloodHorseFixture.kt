package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.github.michaelbull.result.unwrap
import java.time.LocalDate

/**
 * テスト用に [BloodHorse] / [StudBookEntry] を組み立てる Object Mother。
 *
 * 前提条件検証（父=雄・母=雌等）を経ずに任意の性・品種の馬を用意したい（例: [BloodHorse.create] へ渡す父・母、 種付の種牡馬・繁殖牝馬）。父母不明の輸入馬の生成口
 * [BloodHorse.createImported] は前提条件を持たず公開されているため、
 * これを使えば検証を通さずに任意の馬を組み立てられる（テスト上は原産国・揚陸日が付くだけで、性・品種・ID は自由に指定できる）。
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

    /**
     * 既定値を持つ [BloodHorse] を生成する。性・品種など必要な属性のみ上書きする。
     *
     * テストで父・母や種牡馬・繁殖牝馬として使う「既に登録済みの馬」を、前提条件検証を経ずに用意するためのもの。 検証を持たない [BloodHorse.createImported]
     * を経由するため、性・品種・ID は自由に指定できる。
     */
    fun bloodHorse(sex: Sex = Sex.MALE, breedType: BreedType = BreedType.THOROUGHBRED): BloodHorse =
        BloodHorse.createImported(
            entry = importedHorseEntry(sex = sex, breedType = breedType),
            registrationNumber = PedigreeRegistrationNumber.create("2023104567").unwrap(),
        )

    /** 既定値を持つ [ImportedHorseEntry]（輸入馬）を生成する。必要な属性のみ上書きする。 */
    fun importedHorseEntry(
        sex: Sex = Sex.MALE,
        coatColor: CoatColor = CoatColor.BAY,
        breedType: BreedType = BreedType.THOROUGHBRED,
        originCountry: String = "アイルランド",
    ): ImportedHorseEntry =
        ImportedHorseEntry(
            sex = sex,
            coatColor = coatColor,
            breedType = breedType,
            dateOfBirth = DateOfBirth(LocalDate.of(2020, 4, 10)),
            breeder = Breeder.create("Coolmore").unwrap(),
            microchipNumber = MicrochipNumber.create("392140000000002").unwrap(),
            originCountry = OriginCountry.create(originCountry).unwrap(),
            landingDate = LandingDate(LocalDate.of(2024, 9, 1)),
        )

    /** 既定値を持つ父母不明の輸入馬 [BloodHorse] を生成する。性・品種など必要な属性のみ上書きする。 */
    fun importedBloodHorse(
        sex: Sex = Sex.MALE,
        breedType: BreedType = BreedType.THOROUGHBRED,
    ): BloodHorse =
        BloodHorse.createImported(
            entry = importedHorseEntry(sex = sex, breedType = breedType),
            registrationNumber = PedigreeRegistrationNumber.create("2020900001").unwrap(),
        )

    /** 既定値を持つ内国産の [BloodHorse]（父母を ID 参照する [Origin.Domestic]）を生成する。 */
    fun domesticBloodHorse(): BloodHorse =
        BloodHorse.create(
                sire = bloodHorse(sex = Sex.MALE),
                dam = bloodHorse(sex = Sex.FEMALE),
                entry = studBookEntry(),
                registrationNumber = PedigreeRegistrationNumber.create("2023104567").unwrap(),
            )
            .unwrap()
}
