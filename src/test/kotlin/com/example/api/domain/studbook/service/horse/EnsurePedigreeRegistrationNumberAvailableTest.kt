package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumberAlreadyTaken
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class EnsurePedigreeRegistrationNumberAvailableTest {

    private val number = PedigreeRegistrationNumber.create("2023104567").unwrap()

    @Test
    fun `未使用の血統登録番号なら Ok を返す`() {
        val repository =
            mockk<BloodHorseRepository> {
                every { existsByRegistrationNumber(number) } returns false
            }

        val result = ensurePedigreeRegistrationNumberAvailable(number, repository)

        assert(result.isOk)
    }

    @Test
    fun `使用済みの血統登録番号なら PedigreeRegistrationNumberAlreadyTaken を返す`() {
        val repository =
            mockk<BloodHorseRepository> {
                every { existsByRegistrationNumber(number) } returns true
            }

        val error = ensurePedigreeRegistrationNumberAvailable(number, repository).getError()

        assert(error == PedigreeRegistrationNumberAlreadyTaken(number))
    }
}
