package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RegisterCarriedOverHorseUseCaseTest {

    /** すべて正しい既定のペイロード。変種は `copy` で 1 項目だけ差し替える。 */
    private fun validPayload() =
        RegisterCarriedOverHorseCommand(
            sex = Sex.FEMALE,
            coatColor = CoatColor.BLACK,
            breedType = BreedType.THOROUGHBRED,
            dateOfBirth = LocalDate.of(2002, 3, 31),
            breeder = "ノーザンファーム",
            microchipNumber = "392140000000003",
            registrationNumber = "2002100501",
        )

    private fun command(
        payload: RegisterCarriedOverHorseCommand
    ): Command<RegisterCarriedOverHorseCommand> = Command(payload, Instant.now())

    /** 審査ポートのスタブ。`save` は引数（確定済み審査）をそのまま返す。 */
    private fun inspectionRepository() =
        mockk<HorseInspectionRepository> { every { save(any()) } answers { firstArg() } }

    @Nested
    inner class SuccessCase {
        @Test
        fun `正しい入力のとき移行取り込みの馬が登録され永続化される`() {
            val repository =
                mockk<BloodHorseRepository> { every { save(any()) } answers { Ok(firstArg()) } }
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val registered = useCase(command(validPayload())).unwrap()

            val bloodHorse = registered.bloodHorse
            assert(bloodHorse.origin == Origin.CarriedOver)
            assert(bloodHorse.registrationNumber.value == "2002100501")
            assert(registered.inspection.microchipNumber.value == "392140000000003")
            // 親子判定は当システムでは実施しない（先行原簿で確認済みの取り込みのため）
            assert(registered.inspection.parentage == ParentageDetermination.NotApplicable)
            verify(exactly = 1) { repository.save(any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `血統登録番号がブランクのとき InvalidRegistrationNumber を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val result = useCase(command(validPayload().copy(registrationNumber = "")))

            assert(
                result.getError() == RegisterCarriedOverHorseUseCaseError.InvalidRegistrationNumber
            )
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `マイクロチップ番号が不正なとき InvalidMicrochipNumber を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val result = useCase(command(validPayload().copy(microchipNumber = "123")))

            assert(result.getError() == RegisterCarriedOverHorseUseCaseError.InvalidMicrochipNumber)
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `生産者名がブランクのとき BlankBreeder を返し永続化されない`() {
            val repository = mockk<BloodHorseRepository>()
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val result = useCase(command(validPayload().copy(breeder = " ")))

            assert(result.getError() == RegisterCarriedOverHorseUseCaseError.BlankBreeder)
            verify(exactly = 0) { repository.save(any()) }
        }
    }
}
