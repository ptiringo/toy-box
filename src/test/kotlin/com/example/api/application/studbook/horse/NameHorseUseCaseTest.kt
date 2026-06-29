package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
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

    /** 審査ポートのスタブ。命名後の response 組み立て用に既定の審査を返す。 */
    private fun inspectionRepository() =
        mockk<HorseInspectionRepository> {
            every { findById(any()) } returns BloodHorseFixture.inspection()
        }

    @Nested
    inner class SuccessCase {
        @Test
        fun `未命名の馬に命名すると馬名を持つ馬が永続化される`() {
            val horse = BloodHorseFixture.bloodHorse()
            val repository =
                mockk<BloodHorseRepository> {
                    every { findById(horse.id) } returns horse
                    every { existsByName(any()) } returns false
                    every { save(any()) } answers { firstArg() }
                }
            val useCase = NameHorseUseCase(repository, inspectionRepository())

            val registered = useCase(command(horse.id.value, "オグリキャップ")).unwrap()

            assert(registered.bloodHorse.id == horse.id)
            assert(registered.bloodHorse.name?.value == "オグリキャップ")
            verify(exactly = 1) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `馬名が不正なとき InvalidName を返し引当も永続化もしない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = NameHorseUseCase(repository, inspectionRepository())

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
            val useCase = NameHorseUseCase(repository, inspectionRepository())

            val result = useCase(command(id, "オグリキャップ"))

            assert(result.getError() == NameHorseUseCaseError.HorseNotFound(id))
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `馬名が既に使用済みのとき NameAlreadyTaken を返し永続化されない`() {
            val horse = BloodHorseFixture.bloodHorse()
            val repository =
                mockk<BloodHorseRepository> {
                    every { findById(horse.id) } returns horse
                    every { existsByName(HorseName.create("オグリキャップ").unwrap()) } returns true
                }
            val useCase = NameHorseUseCase(repository, inspectionRepository())

            val result = useCase(command(horse.id.value, "オグリキャップ"))

            assert(result.getError() == NameHorseUseCaseError.NameAlreadyTaken("オグリキャップ"))
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
                mockk<BloodHorseRepository> {
                    every { findById(named.id) } returns named
                    every { existsByName(any()) } returns false
                }
            val useCase = NameHorseUseCase(repository, inspectionRepository())

            val result = useCase(command(named.id.value, "トウカイテイオー"))

            assert(result.getError() == NameHorseUseCaseError.AlreadyNamed("オグリキャップ"))
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `命名後の審査が見つからないとき InspectionNotFound を返す`() {
            val horse = BloodHorseFixture.bloodHorse()
            val repository =
                mockk<BloodHorseRepository> {
                    every { findById(horse.id) } returns horse
                    every { existsByName(any()) } returns false
                    every { save(any()) } answers { firstArg() }
                }
            val inspectionRepository =
                mockk<HorseInspectionRepository> { every { findById(any()) } returns null }
            val useCase = NameHorseUseCase(repository, inspectionRepository)

            val result = useCase(command(horse.id.value, "オグリキャップ"))

            assert(
                result.getError() ==
                    NameHorseUseCaseError.InspectionNotFound(horse.inspectionId.value)
            )
        }
    }
}
