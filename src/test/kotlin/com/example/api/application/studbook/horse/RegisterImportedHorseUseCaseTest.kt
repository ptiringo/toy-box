package com.example.api.application.studbook.horse

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
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

class RegisterImportedHorseUseCaseTest {

    private val actor =
        Actor(AccountId(UUID.randomUUID()), setOf(StudbookPermissions.HORSE_REGISTER_IMPORTED))

    /** すべて正しい既定のペイロード。変種は `copy` で 1 項目だけ差し替える。 */
    private fun validPayload() =
        RegisterImportedHorseCommand(
            sex = Sex.MALE,
            coatColor = CoatColor.BAY,
            breedType = BreedType.THOROUGHBRED,
            dateOfBirth = LocalDate.of(2020, 4, 10),
            breeder = "Coolmore",
            microchipNumber = "392140000000002",
            originCountry = "アイルランド",
            landingDate = LocalDate.of(2024, 9, 1),
            registrationNumber = "2020900001",
        )

    private fun command(
        payload: RegisterImportedHorseCommand
    ): Command<RegisterImportedHorseCommand> = Command(payload, Instant.now())

    /** 審査ポートのスタブ。`save` は引数（確定済み審査）をそのまま返す。 */
    private fun inspectionRepository() =
        mockk<HorseInspectionRepository> { every { save(any()) } answers { firstArg() } }

    @Nested
    inner class SuccessCase {
        @Test
        fun `正しい入力のとき父母不明の輸入馬が登録され永続化される`() {
            val repository =
                mockk<BloodHorseRepository> { every { save(any()) } answers { Ok(firstArg()) } }
            val useCase = RegisterImportedHorseUseCase(repository, inspectionRepository())

            val registered = useCase(actor, command(validPayload())).unwrap()

            val bloodHorse = registered.bloodHorse
            val origin = bloodHorse.origin
            assert(origin is Origin.Imported)
            assert((origin as Origin.Imported).originCountry.name == "アイルランド")
            assert(origin.landingDate.value == LocalDate.of(2024, 9, 1))
            assert(bloodHorse.registrationNumber.value == "2020900001")
            assert(registered.inspection.microchipNumber.value == "392140000000002")
            verify(exactly = 1) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `血統登録番号がブランクのとき InvalidRegistrationNumber を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterImportedHorseUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload().copy(registrationNumber = "")))

            assert(result.getError() == RegisterImportedHorseUseCaseError.InvalidRegistrationNumber)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `マイクロチップ番号が不正なとき InvalidMicrochipNumber を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterImportedHorseUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload().copy(microchipNumber = "123")))

            assert(result.getError() == RegisterImportedHorseUseCaseError.InvalidMicrochipNumber)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `原産国がブランクのとき BlankOriginCountry を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterImportedHorseUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload().copy(originCountry = "")))

            assert(result.getError() == RegisterImportedHorseUseCaseError.BlankOriginCountry)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `権限を持たない Actor で呼ぶと Forbidden を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val inspectionRepository = mockk<HorseInspectionRepository>()
            val useCase = RegisterImportedHorseUseCase(repository, inspectionRepository)
            val noPermissionActor = Actor(AccountId(UUID.randomUUID()), emptySet())

            val result = useCase(noPermissionActor, command(validPayload()))

            assert(
                result.getError() ==
                    RegisterImportedHorseUseCaseError.Forbidden(
                        StudbookPermissions.HORSE_REGISTER_IMPORTED
                    )
            )
            verify(exactly = 0) { repository.save(any()) }
            verify(exactly = 0) { inspectionRepository.save(any()) }
        }
    }
}
