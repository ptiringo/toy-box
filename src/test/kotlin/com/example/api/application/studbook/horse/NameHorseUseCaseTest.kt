package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NameHorseUseCaseTest {

    private fun command(bloodHorseId: UUID, name: String): Command<NameHorseCommand> =
        Command(NameHorseCommand(bloodHorseId = bloodHorseId, name = name), Instant.now())

    @Nested
    inner class SuccessCase {
        @Test
        fun `未命名の馬に命名すると馬名を持つ馬が永続化される`() {
            val horse = BloodHorseFixture.bloodHorse()
            val repository =
                mockk<BloodHorseRepository> {
                    every { findById(horse.id) } returns horse
                    every { save(any()) } answers { firstArg() }
                }
            val useCase = NameHorseUseCase(repository)

            val named = useCase(command(horse.id.value, "オグリキャップ")).unwrap()

            assert(named.id == horse.id)
            assert(named.name?.value == "オグリキャップ")
            verify(exactly = 1) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `馬名が不正なとき InvalidName を返し引当も永続化もしない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = NameHorseUseCase(repository)

            val result = useCase(command(UUID.randomUUID(), "ア"))

            assert(result.getError() == NameHorseUseCaseError.InvalidName)
            verify(exactly = 0) { repository.findById(any()) }
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `対象の馬が見つからないとき HorseNotFound を返し永続化されない`() {
            val id = UUID.randomUUID()
            val repository =
                mockk<BloodHorseRepository> { every { findById(BloodHorseId(id)) } returns null }
            val useCase = NameHorseUseCase(repository)

            val result = useCase(command(id, "オグリキャップ"))

            assert(result.getError() == NameHorseUseCaseError.HorseNotFound(id))
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `既に命名済みのとき AlreadyNamed を返し永続化されない`() {
            val named =
                BloodHorseFixture.bloodHorse()
                    .assignName(HorseName.create("オグリキャップ").unwrap())
                    .unwrap()
                    .aggregate
            val repository =
                mockk<BloodHorseRepository> { every { findById(named.id) } returns named }
            val useCase = NameHorseUseCase(repository)

            val result = useCase(command(named.id.value, "トウカイテイオー"))

            assert(result.getError() == NameHorseUseCaseError.AlreadyNamed("オグリキャップ"))
            verify(exactly = 0) { repository.save(any()) }
        }
    }
}
