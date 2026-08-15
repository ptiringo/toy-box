package com.example.api.application.studbook.horse

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
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

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

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
        mockk<HorseInspectionRepository> { every { save(worldId, any()) } answers { secondArg() } }

    @Nested
    inner class SuccessCase {
        @Test
        fun `正しい入力のとき移行取り込みの馬が登録され永続化される`() {
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(worldId, any()) } returns false
                    every { save(worldId, any()) } answers { Ok(secondArg()) }
                }
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val registered = useCase(actor, command(validPayload())).unwrap()

            val bloodHorse = registered.bloodHorse
            assert(bloodHorse.origin == Origin.CarriedOver)
            assert(bloodHorse.registrationNumber.value == "2002100501")
            assert(registered.inspection.microchipNumber.value == "392140000000003")
            // 親子判定は当システムでは実施しない（先行原簿で確認済みの取り込みのため）
            assert(registered.inspection.parentage == ParentageDetermination.NotApplicable)
            verify(exactly = 1) { repository.save(worldId, any()) }
        }
    }

    @Nested
    inner class FailureCase {
        @Test
        fun `血統登録番号がブランクのとき InvalidRegistrationNumber を返し永続化されない`() {
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(worldId, any()) } returns false
                }
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload().copy(registrationNumber = "")))

            assert(
                result.getError() == RegisterCarriedOverHorseUseCaseError.InvalidRegistrationNumber
            )
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `マイクロチップ番号が不正なとき InvalidMicrochipNumber を返し永続化されない`() {
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(worldId, any()) } returns false
                }
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload().copy(microchipNumber = "123")))

            assert(result.getError() == RegisterCarriedOverHorseUseCaseError.InvalidMicrochipNumber)
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `引き継いだ血統登録番号が取り込み先で採番済みのとき RegistrationNumberAlreadyTaken を返す`() {
            // 先行原簿で交付済みの番号を引き継ぐ経路でも、取り込み先の原簿の中では一意でなければならない。
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(worldId, any()) } returns true
                }
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload()))

            val expected =
                RegisterCarriedOverHorseUseCaseError.RegistrationNumberAlreadyTaken("2002100501")
            assert(result.getError() == expected)
            verify(exactly = 0) { repository.save(worldId, any()) }
        }

        @Test
        fun `生産者名がブランクのとき BlankBreeder を返し永続化されない`() {
            val repository =
                mockk<BloodHorseRepository> {
                    every { existsByRegistrationNumber(worldId, any()) } returns false
                }
            val useCase = RegisterCarriedOverHorseUseCase(repository, inspectionRepository())

            val result = useCase(actor, command(validPayload().copy(breeder = " ")))

            assert(result.getError() == RegisterCarriedOverHorseUseCaseError.BlankBreeder)
            verify(exactly = 0) { repository.save(worldId, any()) }
        }
    }
}
