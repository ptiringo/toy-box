package com.example.api.application.studbook.horse

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RegisterInStudBookUseCaseTest {

    private val actor =
        Actor(AccountId(UUID.randomUUID()), setOf(StudbookPermissions.HORSE_REGISTER))

    /** すべて正しい既定のペイロード。変種は `copy` で 1 項目だけ差し替える。 */
    private fun validPayload(sireId: UUID, damId: UUID) =
        RegisterInStudBookCommand(
            sireId = sireId,
            damId = damId,
            sex = Sex.MALE,
            coatColor = CoatColor.BAY,
            breedType = BreedType.THOROUGHBRED,
            dateOfBirth = LocalDate.of(2023, 3, 15),
            breeder = "ノーザンファーム",
            microchipNumber = "392140000000001",
            dnaParentage = DnaParentageResult.CONSISTENT,
            registrationNumber = "2023104567",
        )

    private fun command(payload: RegisterInStudBookCommand): Command<RegisterInStudBookCommand> =
        Command(payload, Instant.now())

    /** 審査ポートのスタブ。`save` は引数（確定済み審査）をそのまま返す。 */
    private fun inspectionRepository() =
        mockk<HorseInspectionRepository> { every { save(any()) } answers { firstArg() } }

    @Nested
    inner class SuccessCase {
        @Test
        fun `前提条件を満たすとき血統登録に成功し父母を ID で参照する馬が永続化される`() {
            val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
            val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                    every { findAllById(setOf(sire.id, dam.id)) } returns
                        mapOf(sire.id to sire, dam.id to dam)
                    every { save(any()) } answers { Ok(firstArg()) }
                }
            val useCase = RegisterInStudBookUseCase(repository, inspectionRepository())

            val registered =
                useCase(actor, command(validPayload(sire.id.value, dam.id.value))).unwrap()

            val bloodHorse = registered.bloodHorse
            assert(bloodHorse.origin == Origin.Domestic(sireId = sire.id, damId = dam.id))
            assert(bloodHorse.breedType == BreedType.THOROUGHBRED)
            assert(bloodHorse.registrationNumber.value == "2023104567")
            assert(bloodHorse.inspectionId == registered.inspection.id)
            assert(registered.inspection.microchipNumber.value == "392140000000001")
            // 父・母の引き当ては逐次 findById ではなく 1 回の一括 lookup で行う。
            verify(exactly = 1) { repository.findAllById(setOf(sire.id, dam.id)) }
            verify(exactly = 1) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `血統登録番号がブランクのとき InvalidRegistrationNumber を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterInStudBookUseCase(repository, inspectionRepository())

            val result =
                useCase(
                    actor,
                    command(
                        validPayload(UUID.randomUUID(), UUID.randomUUID())
                            .copy(registrationNumber = "")
                    ),
                )

            assert(result.getError() == RegisterInStudBookUseCaseError.InvalidRegistrationNumber)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `マイクロチップ番号が不正なとき InvalidMicrochipNumber を返し永続化されない`() {
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                }
            val useCase = RegisterInStudBookUseCase(repository, inspectionRepository())

            val result =
                useCase(
                    actor,
                    command(
                        validPayload(UUID.randomUUID(), UUID.randomUUID())
                            .copy(microchipNumber = "123")
                    ),
                )

            assert(result.getError() == RegisterInStudBookUseCaseError.InvalidMicrochipNumber)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `血統登録番号が既に使用済みのとき RegistrationNumberAlreadyTaken を返し永続化されない`() {
            val bloodHorseRepository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(any()) } returns true
                }
            val horseInspectionRepository = mockk<HorseInspectionRepository>()
            val useCase = RegisterInStudBookUseCase(bloodHorseRepository, horseInspectionRepository)

            val result = useCase(actor, command(validPayload(UUID.randomUUID(), UUID.randomUUID())))

            assert(
                result.getError() ==
                    RegisterInStudBookUseCaseError.RegistrationNumberAlreadyTaken("2023104567")
            )
            verify(exactly = 0) { bloodHorseRepository.save(any()) }
        }

        @Test
        fun `父が見つからないとき SireNotFound を返し永続化されない`() {
            val sireId = UUID.randomUUID()
            val damId = UUID.randomUUID()
            val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                    every { findAllById(setOf(BloodHorseId(sireId), BloodHorseId(damId))) } returns
                        mapOf(BloodHorseId(damId) to dam)
                }
            val useCase = RegisterInStudBookUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload(sireId, damId)))

            assert(result.getError() == RegisterInStudBookUseCaseError.SireNotFound(sireId))
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `母が見つからないとき DamNotFound を返し永続化されない`() {
            val sireId = UUID.randomUUID()
            val damId = UUID.randomUUID()
            val sire = BloodHorseFixture.bloodHorse(sex = Sex.MALE)
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                    every { findAllById(setOf(BloodHorseId(sireId), BloodHorseId(damId))) } returns
                        mapOf(BloodHorseId(sireId) to sire)
                }
            val useCase = RegisterInStudBookUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload(sireId, damId)))

            assert(result.getError() == RegisterInStudBookUseCaseError.DamNotFound(damId))
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `父が雄でないときドメイン検証違反を PreconditionViolated に wrap して返す`() {
            val sire = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                    every { findAllById(setOf(sire.id, dam.id)) } returns
                        mapOf(sire.id to sire, dam.id to dam)
                }
            val useCase = RegisterInStudBookUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload(sire.id.value, dam.id.value)))

            assert(
                result.getError() ==
                    RegisterInStudBookUseCaseError.PreconditionViolated(
                        RegisterInStudBookError.SireNotMale
                    )
            )
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `前提条件違反で登録が失敗したとき審査が保存されない`() {
            // 父が雌のため BloodHorse.create が SireNotMale を返す → 審査 save に到達しない
            val sire = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val dam = BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)
            val bloodHorseRepository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(any()) } returns false
                    every { findAllById(setOf(sire.id, dam.id)) } returns
                        mapOf(sire.id to sire, dam.id to dam)
                }
            val inspectionRepository = mockk<HorseInspectionRepository>()
            val useCase = RegisterInStudBookUseCase(bloodHorseRepository, inspectionRepository)

            useCase(actor, command(validPayload(sire.id.value, dam.id.value)))

            verify(exactly = 0) { inspectionRepository.save(any()) }
        }

        @Test
        fun `権限を持たない Actor で呼ぶと Forbidden を返し引き当ても永続化もしない`() {
            val bloodHorseRepository = mockk<BloodHorseRepository>()
            val inspectionRepository = mockk<HorseInspectionRepository>()
            val useCase = RegisterInStudBookUseCase(bloodHorseRepository, inspectionRepository)
            val noPermissionActor = Actor(AccountId(UUID.randomUUID()), emptySet())

            val result =
                useCase(
                    noPermissionActor,
                    command(validPayload(UUID.randomUUID(), UUID.randomUUID())),
                )

            assert(
                result.getError() ==
                    RegisterInStudBookUseCaseError.Forbidden(StudbookPermissions.HORSE_REGISTER)
            )
            verify(exactly = 0) { bloodHorseRepository.findAllById(any()) }
            verify(exactly = 0) { bloodHorseRepository.save(any()) }
            verify(exactly = 0) { inspectionRepository.save(any()) }
        }
    }
}
