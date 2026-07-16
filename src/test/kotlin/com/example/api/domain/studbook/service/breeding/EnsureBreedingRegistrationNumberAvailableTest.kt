package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumberAlreadyTaken
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class EnsureBreedingRegistrationNumberAvailableTest {

    private val number = BreedingRegistrationNumber.create("B-2024-0001").unwrap()

    @Test
    fun `未使用の繁殖登録番号なら Ok を返す`() {
        val repository =
            mockk<BreedingRegistrationRepository> {
                every { existsByRegistrationNumber(number) } returns false
            }

        assert(ensureBreedingRegistrationNumberAvailable(number, repository).isOk)
    }

    @Test
    fun `使用済みの繁殖登録番号なら BreedingRegistrationNumberAlreadyTaken を返す`() {
        val repository =
            mockk<BreedingRegistrationRepository> {
                every { existsByRegistrationNumber(number) } returns true
            }

        val error = ensureBreedingRegistrationNumberAvailable(number, repository).getError()

        assert(error == BreedingRegistrationNumberAlreadyTaken(number))
    }
}
