package com.example.api.domain.studbook.service.horse

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.NameHorseError
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())

class NameHorseTest {
    private val name = HorseName.create("オグリキャップ").unwrap()

    @Test
    fun `未使用の馬名なら命名済みの状態遷移を返す`() {
        val horse = BloodHorseFixture.bloodHorse() // 未命名
        val repository =
            mockk<BloodHorseRepository> { every { existsByName(worldId, name) } returns false }

        val transition = nameHorse(worldId, horse, name, repository).unwrap()

        assert(transition.aggregate.id == horse.id)
        assert(transition.aggregate.name == name)
        assert(transition.event.name == name)
    }

    @Test
    fun `既に使用済みの馬名なら NameAlreadyTaken を返し命名しない`() {
        val horse = BloodHorseFixture.bloodHorse() // 未命名
        val repository =
            mockk<BloodHorseRepository> { every { existsByName(worldId, name) } returns true }

        val error = nameHorse(worldId, horse, name, repository).getError()

        assert(error == NameHorseError.NameAlreadyTaken(name))
    }

    @Test
    fun `対象が既に命名済みなら AlreadyNamed を返す`() {
        val existing = HorseName.create("トウカイテイオー").unwrap()
        val named = BloodHorseFixture.bloodHorse().assignName(existing).unwrap().aggregate
        val repository =
            mockk<BloodHorseRepository> { every { existsByName(worldId, name) } returns false }

        val error = nameHorse(worldId, named, name, repository).getError()

        assert(error == NameHorseError.AlreadyNamed(existing))
    }
}
