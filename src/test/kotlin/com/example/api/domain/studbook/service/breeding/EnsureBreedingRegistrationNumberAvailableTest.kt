package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumberAlreadyTaken
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class EnsureBreedingRegistrationNumberAvailableTest {
    private val worldId = WorldId(generateId())
    private val number = BreedingRegistrationNumber.create("BR-0000430846").unwrap()

    @Test
    fun `未使用の繁殖登録番号なら Ok を返す`() {
        val repository =
            mockk<BreedingRegistrationRepository> {
                every { existsByRegistrationNumber(worldId, number) } returns false
            }

        val result = ensureBreedingRegistrationNumberAvailable(worldId, number, repository)

        assert(result.isOk)
    }

    @Test
    fun `既に採番済みの繁殖登録番号なら BreedingRegistrationNumberAlreadyTaken を返す`() {
        val repository =
            mockk<BreedingRegistrationRepository> {
                every { existsByRegistrationNumber(worldId, number) } returns true
            }

        val error =
            ensureBreedingRegistrationNumberAvailable(worldId, number, repository).getError()

        assert(error == BreedingRegistrationNumberAlreadyTaken(number))
    }

    @Test
    fun `照合は引数で受けた世界の中に閉じる`() {
        // 同じ番号でも、別の世界（別プレイヤーのセーブデータ）で採番済みかは問わない。
        val otherWorldId = WorldId(generateId())
        val repository =
            mockk<BreedingRegistrationRepository> {
                every { existsByRegistrationNumber(worldId, number) } returns false
                every { existsByRegistrationNumber(otherWorldId, number) } returns true
            }

        val inOtherWorld =
            ensureBreedingRegistrationNumberAvailable(otherWorldId, number, repository).getError()

        assert(ensureBreedingRegistrationNumberAvailable(worldId, number, repository).isOk)
        assert(inOtherWorld == BreedingRegistrationNumberAlreadyTaken(number))
    }
}
