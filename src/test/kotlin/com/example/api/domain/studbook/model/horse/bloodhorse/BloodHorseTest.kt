package com.example.api.domain.studbook.model.horse.bloodhorse

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** BloodHorse 集約の馬名登録（assignName）に関するユニットテスト。 */
class BloodHorseTest {
    private val name = HorseName.create("オグリキャップ").unwrap()

    @Test
    fun `未命名の馬に命名すると馬名を持つ新しい個体が返り 同一性は引き継がれる`() {
        val unnamed = BloodHorseFixture.bloodHorse()

        val named = unnamed.assignName(name).unwrap()

        assert(named.name == name)
        assert(named.id == unnamed.id)
        // 他の属性も引き継がれる
        assert(named.registrationNumber == unnamed.registrationNumber)
        assert(named.origin == unnamed.origin)
    }

    @Test
    fun `assignName は元の個体を変更しない（イミュータブル）`() {
        val unnamed = BloodHorseFixture.bloodHorse()

        unnamed.assignName(name).unwrap()

        assert(unnamed.name == null)
    }

    @Test
    fun `命名済みの馬への再命名は HorseAlreadyNamed を返し 馬名は変わらない`() {
        val named = BloodHorseFixture.bloodHorse().assignName(name).unwrap()
        val another = HorseName.create("トウカイテイオー").unwrap()

        val result = named.assignName(another)

        assert(result.getError() == HorseAlreadyNamed(name))
        assert(named.name == name)
    }
}
