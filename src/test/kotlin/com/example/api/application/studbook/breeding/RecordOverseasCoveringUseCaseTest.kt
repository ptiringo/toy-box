package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.Covering
import com.example.api.domain.studbook.model.breeding.RecordOverseasCoveringError
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

class RecordOverseasCoveringUseCaseTest {
    /** すべて正しい既定のペイロード。変種は `copy` で 1 項目だけ差し替える。 */
    private fun validPayload(breedingRegistrationId: UUID) =
        RecordOverseasCoveringCommand(
            breedingRegistrationId = breedingRegistrationId,
            sire =
                ForeignSireInput(
                    name = "Danehill",
                    studBookCountry = "アメリカ",
                    breedType = BreedType.THOROUGHBRED,
                    coatColor = CoatColor.BAY,
                ),
            coveringDate = LocalDate.of(1999, 4, 10),
            coveringCountry = "アイルランド",
            proofNumber = "IE-1999-0004",
        )

    private fun command(
        payload: RecordOverseasCoveringCommand
    ): Command<RecordOverseasCoveringCommand> = Command(payload, Instant.now())

    /** 繁殖牝馬の繁殖登録が引き当たり、同年の既存成績が無い正常系の土台を組む。 */
    private class Wiring(
        val registration: BreedingRegistration = BreedingFixture.breedingRegistration()
    ) {
        val registrationRepository =
            mockk<BreedingRegistrationRepository> {
                every { findById(worldId, registration.id) } returns registration
            }
        val breedingResultRepository =
            mockk<BreedingResultRepository> {
                every { findByBreedingRegistrationIdAndBreedingYear(worldId, any(), any()) } returns
                    null
                every { save(worldId, any()) } answers { Ok(secondArg()) }
            }

        fun useCase() =
            RecordOverseasCoveringUseCase(registrationRepository, breedingResultRepository)
    }

    @Nested
    inner class SuccessCase {
        @Test
        fun `前提条件を満たすとき海外種付が記録され分娩結果未報告の繁殖成績が永続化される`() {
            val w = Wiring()

            val result = w.useCase()(actor, command(validPayload(w.registration.id.value))).unwrap()

            val covering = result.covering
            check(covering is Covering.Overseas)
            assert(covering.sire == BloodHorseFixture.foreignSire())
            assert(covering.coveringDate == LocalDate.of(1999, 4, 10))
            assert(covering.coveringCountry.name == "アイルランド")
            assert(covering.proofNumber.value == "IE-1999-0004")
            assert(result.breedingYear == Year.of(1999))
            assert(result.outcome == null)
            verify(exactly = 1) { w.breedingResultRepository.save(worldId, any()) }
        }
    }

    @Nested
    inner class ValidationFailureCase {
        private fun recordWith(
            transform: (RecordOverseasCoveringCommand) -> RecordOverseasCoveringCommand
        ): Pair<Wiring, RecordOverseasCoveringUseCaseError?> {
            val w = Wiring()
            val result =
                w.useCase()(actor, command(transform(validPayload(w.registration.id.value))))
            return w to result.getError()
        }

        @Test
        fun `外国種牡馬の馬名がブランクだと BlankSireName を返し永続化しない`() {
            val (w, error) = recordWith { it.copy(sire = it.sire.copy(name = " ")) }

            assert(error == RecordOverseasCoveringUseCaseError.BlankSireName)
            verify(exactly = 0) { w.breedingResultRepository.save(worldId, any()) }
        }

        @Test
        fun `外国種牡馬の登録機関の国がブランクだと BlankSireStudBookCountry を返す`() {
            val (_, error) = recordWith { it.copy(sire = it.sire.copy(studBookCountry = "")) }

            assert(error == RecordOverseasCoveringUseCaseError.BlankSireStudBookCountry)
        }

        @Test
        fun `種付国がブランクだと BlankCoveringCountry を返す`() {
            val (_, error) = recordWith { it.copy(coveringCountry = "") }

            assert(error == RecordOverseasCoveringUseCaseError.BlankCoveringCountry)
        }

        @Test
        fun `種付事実の証明の番号がブランクだと BlankProofNumber を返す`() {
            val (_, error) = recordWith { it.copy(proofNumber = "") }

            assert(error == RecordOverseasCoveringUseCaseError.BlankProofNumber)
        }
    }

    @Nested
    inner class ReferenceFailureCase {
        @Test
        fun `繁殖登録が存在しないと BreedingRegistrationNotFound を返し永続化しない`() {
            val w = Wiring()
            val missing = generateId()
            every {
                w.registrationRepository.findById(worldId, BreedingRegistrationId(missing))
            } returns null

            val result = w.useCase()(actor, command(validPayload(missing)))

            assert(
                result.getError() ==
                    RecordOverseasCoveringUseCaseError.BreedingRegistrationNotFound(missing)
            )
            verify(exactly = 0) { w.breedingResultRepository.save(worldId, any()) }
        }
    }

    @Nested
    inner class PreconditionFailureCase {
        @Test
        fun `繁殖登録のロールが繁殖牝馬でないと NotBroodmare を wrap して返す`() {
            val w = Wiring(registration = BreedingFixture.stallionRegistration())

            val result = w.useCase()(actor, command(validPayload(w.registration.id.value)))

            assert(
                result.getError() ==
                    RecordOverseasCoveringUseCaseError.PreconditionViolated(
                        RecordOverseasCoveringError.NotBroodmare
                    )
            )
            verify(exactly = 0) { w.breedingResultRepository.save(worldId, any()) }
        }

        @Test
        fun `同じ繁殖年の成績が既にあると AlreadyRecordedForYear を wrap して返す`() {
            val w = Wiring()
            val existing = BreedingFixture.uncoveredBreedingResult(w.registration, Year.of(1999))
            every {
                w.breedingResultRepository.findByBreedingRegistrationIdAndBreedingYear(
                    worldId,
                    w.registration.id,
                    Year.of(1999),
                )
            } returns existing

            val result = w.useCase()(actor, command(validPayload(w.registration.id.value)))

            assert(
                result.getError() ==
                    RecordOverseasCoveringUseCaseError.PreconditionViolated(
                        RecordOverseasCoveringError.AlreadyRecordedForYear(
                            Year.of(1999),
                            existing.id,
                        )
                    )
            )
            verify(exactly = 0) { w.breedingResultRepository.save(worldId, any()) }
        }
    }
}
