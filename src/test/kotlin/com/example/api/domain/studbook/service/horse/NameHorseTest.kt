package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.NameHorseError
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class NameHorseTest {

    private val name = HorseName.create("オグリキャップ").unwrap()

    @Test
    fun `未使用の馬名なら命名済みの状態遷移を返す`() {
        val horse = BloodHorseFixture.bloodHorse() // 未命名
        val repository = mockk<BloodHorseRepository> { every { existsByName(name) } returns false }

        val transition = nameHorse(horse, name, repository).unwrap()

        assert(transition.aggregate.id == horse.id)
        assert(transition.aggregate.name == name)
        assert(transition.event.name == name)
    }

    @Test
    fun `既に使用済みの馬名なら NameAlreadyTaken を返し命名しない`() {
        val horse = BloodHorseFixture.bloodHorse() // 未命名
        val repository = mockk<BloodHorseRepository> { every { existsByName(name) } returns true }

        val error = nameHorse(horse, name, repository).getError()

        assert(error == NameHorseError.NameAlreadyTaken(name))
    }

    @Test
    fun `対象が既に命名済みなら AlreadyNamed を返す`() {
        val existing = HorseName.create("トウカイテイオー").unwrap()
        val named = BloodHorseFixture.bloodHorse().assignName(existing).unwrap().aggregate
        val repository = mockk<BloodHorseRepository> { every { existsByName(name) } returns false }

        val error = nameHorse(named, name, repository).getError()

        assert(error == NameHorseError.AlreadyNamed(existing))
    }
}
