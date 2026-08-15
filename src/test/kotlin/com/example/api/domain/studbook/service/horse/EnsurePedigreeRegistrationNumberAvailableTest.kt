package com.example.api.domain.studbook.service.horse

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumberAlreadyTaken
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class EnsurePedigreeRegistrationNumberAvailableTest {
    private val worldId = WorldId(generateId())
    private val number = PedigreeRegistrationNumber.create("2023104567").unwrap()

    @Test
    fun `未使用の血統登録番号なら Ok を返す`() {
        val repository =
            mockk<BloodHorseRepository> {
                every { existsByRegistrationNumber(worldId, number) } returns false
            }

        val result = ensurePedigreeRegistrationNumberAvailable(worldId, number, repository)

        assert(result.isOk)
    }

    @Test
    fun `既に採番済みの血統登録番号なら PedigreeRegistrationNumberAlreadyTaken を返す`() {
        val repository =
            mockk<BloodHorseRepository> {
                every { existsByRegistrationNumber(worldId, number) } returns true
            }

        val error =
            ensurePedigreeRegistrationNumberAvailable(worldId, number, repository).getError()

        assert(error == PedigreeRegistrationNumberAlreadyTaken(number))
    }

    @Test
    fun `照合は引数で受けた世界の中に閉じる`() {
        // 同じ番号でも、別の世界（別プレイヤーのセーブデータ）で採番済みかは問わない。
        val otherWorldId = WorldId(generateId())
        val repository =
            mockk<BloodHorseRepository> {
                every { existsByRegistrationNumber(worldId, number) } returns false
                every { existsByRegistrationNumber(otherWorldId, number) } returns true
            }

        val inOtherWorld =
            ensurePedigreeRegistrationNumberAvailable(otherWorldId, number, repository).getError()

        assert(ensurePedigreeRegistrationNumberAvailable(worldId, number, repository).isOk)
        assert(inOtherWorld == PedigreeRegistrationNumberAlreadyTaken(number))
    }
}
