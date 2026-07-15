package com.example.api.application.studbook.inspection

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 記録ユースケース [RecordHorseInspectionUseCase] の単体テスト。
 *
 * 書き込みポート [HorseInspectionRepository] を mockk（strict）でスタブし、正常系は save へ渡る集約の中身を、 マイクロチップ不正は Err と
 * save 未到達（strict mockk が保証）を検証する（testing.md）。
 */
class RecordHorseInspectionUseCaseTest {
    private val horseInspectionRepository = mockk<HorseInspectionRepository>()
    private val recordHorseInspection = RecordHorseInspectionUseCase(horseInspectionRepository)
    private val actor =
        Actor(AccountId(UUID.randomUUID()), setOf(StudbookPermissions.INSPECTION_RECORD))

    private fun command(
        microchipNumber: String,
        parentage: ParentageDetermination =
            ParentageDetermination.ByDna(DnaParentageResult.CONSISTENT),
        features: IdentificationFeatures? = null,
    ): Command<RecordHorseInspectionCommand> =
        Command(
            RecordHorseInspectionCommand(
                microchipNumber = microchipNumber,
                parentage = parentage,
                features = features,
            ),
            Instant.now(),
        )

    @Test
    fun `正しい入力で審査が保存され保存後の集約がOkで返る`() {
        val saved = slot<HorseInspection>()
        every { horseInspectionRepository.save(capture(saved)) } answers { saved.captured }
        val features = IdentificationFeatures("頭部正中", "左後一白", null)

        val result =
            recordHorseInspection(
                actor,
                command(
                    microchipNumber = "392140000000001",
                    parentage = ParentageDetermination.ByBloodType,
                    features = features,
                ),
            )

        val inspection = result.get()
        assert(inspection != null)
        assert(inspection!!.microchipNumber.value == "392140000000001")
        assert(inspection.parentage == ParentageDetermination.ByBloodType)
        assert(inspection.features == features)
        assert(saved.captured == inspection)
    }

    @Test
    fun `マイクロチップ番号が15桁数字でなければInvalidMicrochipを返し保存しない`() {
        val result = recordHorseInspection(actor, command(microchipNumber = "123"))

        // save をスタブしていない strict mockk のため、save に到達すればここより前に失敗する
        assert(result.getError() == RecordHorseInspectionUseCaseError.InvalidMicrochip)
    }

    @Nested
    inner class AuthorizationFailureCase {
        @Test
        fun `権限を持たない Actor で呼ぶと Forbidden を返し保存しない`() {
            val noPermissionActor = Actor(AccountId(UUID.randomUUID()), emptySet())

            val result =
                recordHorseInspection(
                    noPermissionActor,
                    command(microchipNumber = "392140000000001"),
                )

            assert(
                result.getError() ==
                    RecordHorseInspectionUseCaseError.Forbidden(
                        StudbookPermissions.INSPECTION_RECORD
                    )
            )
            verify(exactly = 0) { horseInspectionRepository.save(any()) }
        }
    }
}
