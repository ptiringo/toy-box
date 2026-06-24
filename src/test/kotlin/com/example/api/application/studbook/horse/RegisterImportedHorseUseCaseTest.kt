package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RegisterImportedHorseUseCaseTest {

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

    @Nested
    inner class SuccessCase {
        @Test
        fun `正しい入力のとき父母不明の輸入馬が登録され永続化される`() {
            val repository =
                mockk<BloodHorseRepository> { every { save(any()) } answers { firstArg() } }
            val useCase = RegisterImportedHorseUseCase(repository)

            val bloodHorse = useCase(command(validPayload())).unwrap()

            val origin = bloodHorse.origin
            assert(origin is Origin.Imported)
            assert((origin as Origin.Imported).originCountry.name == "アイルランド")
            assert(origin.landingDate.value == LocalDate.of(2024, 9, 1))
            assert(bloodHorse.registrationNumber.value == "2020900001")
            verify(exactly = 1) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `血統登録番号がブランクのとき InvalidRegistrationNumber を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterImportedHorseUseCase(repository)

            val result = useCase(command(validPayload().copy(registrationNumber = "")))

            assert(result.getError() == RegisterImportedHorseUseCaseError.InvalidRegistrationNumber)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `マイクロチップ番号が不正なとき InvalidMicrochipNumber を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterImportedHorseUseCase(repository)

            val result = useCase(command(validPayload().copy(microchipNumber = "123")))

            assert(result.getError() == RegisterImportedHorseUseCaseError.InvalidMicrochipNumber)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `原産国がブランクのとき BlankOriginCountry を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterImportedHorseUseCase(repository)

            val result = useCase(command(validPayload().copy(originCountry = "")))

            assert(result.getError() == RegisterImportedHorseUseCaseError.BlankOriginCountry)
            verify(exactly = 0) { repository.save(any()) }
        }
    }
}
