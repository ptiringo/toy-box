package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.studbook.model.breeding.BreedingResultId
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** BloodHorse 集約の馬名登録（assignName）に関するユニットテスト。 */
class BloodHorseTest {
    private val name = HorseName.create("オグリキャップ").unwrap()

    @Test
    fun `未命名の馬に命名すると馬名を持つ新しい個体が返り 同一性は引き継がれる`() {
        val unnamed = BloodHorseFixture.bloodHorse()

        val named = unnamed.assignName(name).unwrap().aggregate

        assert(named.name == name)
        assert(named.id == unnamed.id)
        // 他の属性も引き継がれる
        assert(named.registrationNumber == unnamed.registrationNumber)
        assert(named.origin == unnamed.origin)
    }

    @Test
    fun `create に渡した登録枠を bredFrom として持つ`() {
        val slot = FoalSlot(BreedingResultId(UUID.randomUUID()), FoalPosition.PRIMARY)
        val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
        val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)

        val foal =
            BloodHorse.create(
                    sire,
                    dam,
                    BloodHorseFixture.studBookEntry(),
                    BloodHorseFixture.inspection(),
                    PedigreeRegistrationNumber.create("2023104567").unwrap(),
                    bredFrom = slot,
                )
                .unwrap()

        assert(foal.bredFrom == slot)
    }

    @Test
    fun `createImported の bredFrom は常に null`() {
        assert(BloodHorseFixture.importedBloodHorse().bredFrom == null)
    }

    @Test
    fun `輸入馬の出自で bredFrom を持たせると reconstitute が IllegalArgumentException`() {
        val imported = BloodHorseFixture.importedBloodHorse()

        assertThrows<IllegalArgumentException> {
            BloodHorse.reconstitute(
                id = imported.id,
                registrationNumber = imported.registrationNumber,
                sex = imported.sex,
                coatColor = imported.coatColor,
                breedType = imported.breedType,
                dateOfBirth = imported.dateOfBirth,
                breeder = imported.breeder,
                inspectionId = imported.inspectionId,
                origin = imported.origin,
                name = imported.name,
                bredFrom = FoalSlot(BreedingResultId(UUID.randomUUID()), FoalPosition.PRIMARY),
                version = 1L,
            )
        }
    }

    @Test
    fun `命名しても bredFrom を引き継ぐ`() {
        val base = BloodHorseFixture.domesticBloodHorse()
        val slot = FoalSlot(BreedingResultId(UUID.randomUUID()), FoalPosition.TWIN)
        val withSlot =
            BloodHorse.reconstitute(
                id = base.id,
                registrationNumber = base.registrationNumber,
                sex = base.sex,
                coatColor = base.coatColor,
                breedType = base.breedType,
                dateOfBirth = base.dateOfBirth,
                breeder = base.breeder,
                inspectionId = base.inspectionId,
                origin = base.origin,
                name = null,
                bredFrom = slot,
                version = 1L,
            )

        val named = withSlot.assignName(name).unwrap().aggregate

        assert(named.bredFrom == slot)
    }

    @Test
    fun `createで生成した直後は楽観ロックversionがnull`() {
        val unnamed = BloodHorseFixture.bloodHorse()

        assert(unnamed.version == null)
    }

    @Test
    fun `assignNameで得た新インスタンスは楽観ロックversionを引き継ぐ`() {
        val base = BloodHorseFixture.bloodHorse()
        val persisted =
            BloodHorse.reconstitute(
                id = base.id,
                registrationNumber = base.registrationNumber,
                sex = base.sex,
                coatColor = base.coatColor,
                breedType = base.breedType,
                dateOfBirth = base.dateOfBirth,
                breeder = base.breeder,
                inspectionId = base.inspectionId,
                origin = base.origin,
                name = base.name,
                version = 3L,
            )

        val named = persisted.assignName(name).unwrap().aggregate

        assert(named.version == 3L)
    }

    @Test
    fun `命名に成功すると 命名された個体に対応する HorseNamed イベントが同梱される`() {
        val unnamed = BloodHorseFixture.bloodHorse()

        val transition = unnamed.assignName(name).unwrap()

        assert(transition.aggregate.name == name)
        assert(transition.event == HorseNamed(unnamed.id, name))
    }

    @Test
    fun `assignName は元の個体を変更しない（イミュータブル）`() {
        val unnamed = BloodHorseFixture.bloodHorse()

        unnamed.assignName(name).unwrap()

        assert(unnamed.name == null)
    }

    @Test
    fun `命名済みの馬への再命名は HorseAlreadyNamed を返し 馬名は変わらない（イベントも生成しない）`() {
        val named = BloodHorseFixture.bloodHorse().assignName(name).unwrap().aggregate
        val another = HorseName.create("トウカイテイオー").unwrap()

        val result = named.assignName(another)

        assert(result.getError() == HorseAlreadyNamed(name))
        assert(named.name == name)
    }
}
