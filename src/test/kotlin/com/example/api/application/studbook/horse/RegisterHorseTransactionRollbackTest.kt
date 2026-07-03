package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.DnaParentageResult
import com.example.api.infrastructure.studbook.horse.BloodHorseSpringDataRepository
import com.example.api.infrastructure.studbook.horse.JdbcBloodHorseRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 血統登録系ユースケースのトランザクション境界（#483）の統合テスト。
 *
 * 審査（HorseInspection）→ 軽種馬（BloodHorse）の 2 集約書き込みで、2 番目の save がインフラ障害で 失敗したとき、先行した審査 save
 * がロールバックされ孤児が残らないことを、実 PostgreSQL （Testcontainers）と実トランザクションマネージャで検証する。障害は BloodHorseRepository の
 *
 * @Primary デコレータ Bean で注入する（microchip_number に一意制約が無く実 DB 制約では再現できないため）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class RegisterHorseTransactionRollbackTest(
    private val registerInStudBook: RegisterInStudBookUseCase,
    private val registerImportedHorse: RegisterImportedHorseUseCase,
    private val failingRepository: FailingBloodHorseRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val bloodHorseRows: BloodHorseSpringDataRepository,
) : PostgresContainerSupport() {

    @TestConfiguration
    class FailingSaveConfiguration {
        @Bean
        @Primary
        fun failingBloodHorseRepository(
            delegate: JdbcBloodHorseRepository
        ): FailingBloodHorseRepository = FailingBloodHorseRepository(delegate)
    }

    @BeforeEach
    fun cleanUp() {
        failingRepository.failOnSave = false
        inspectionRows.deleteAll()
        bloodHorseRows.deleteAll()
    }

    @Test
    fun `輸入馬血統登録で軽種馬の保存がインフラ障害で失敗すると先行する審査の保存もロールバックされる`() {
        failingRepository.failOnSave = true

        assertThrows<DataAccessResourceFailureException> {
            registerImportedHorse(command(importedHorseCommand()))
        }

        assert(inspectionRows.count() == 0L) { "審査が孤児として残っている" }
        assert(bloodHorseRows.count() == 0L)
    }

    @Test
    fun `内国産血統登録で軽種馬の保存がインフラ障害で失敗すると先行する審査の保存もロールバックされる`() {
        val sire = failingRepository.save(BloodHorseFixture.bloodHorse(sex = Sex.MALE)).unwrap()
        val dam = failingRepository.save(BloodHorseFixture.bloodHorse(sex = Sex.FEMALE)).unwrap()
        failingRepository.failOnSave = true

        assertThrows<DataAccessResourceFailureException> {
            registerInStudBook(command(domesticCommand(sire, dam)))
        }

        assert(inspectionRows.count() == 0L) { "審査が孤児として残っている" }
        assert(bloodHorseRows.count() == 2L) { "父・母の 2 頭だけが残るはず" }
    }

    private fun <T> command(payload: T) = Command(payload, Instant.parse("2026-07-03T00:00:00Z"))

    private fun importedHorseCommand() =
        RegisterImportedHorseCommand(
            sex = Sex.MALE,
            coatColor = CoatColor.BAY,
            breedType = BreedType.THOROUGHBRED,
            dateOfBirth = LocalDate.of(2020, 4, 10),
            breeder = "Coolmore",
            microchipNumber = "392140000000002",
            originCountry = "アイルランド",
            landingDate = LocalDate.of(2024, 9, 1),
            registrationNumber = "2020900002",
        )

    private fun domesticCommand(sire: BloodHorse, dam: BloodHorse) =
        RegisterInStudBookCommand(
            sireId = sire.id.value,
            damId = dam.id.value,
            sex = Sex.MALE,
            coatColor = CoatColor.BAY,
            breedType = BreedType.THOROUGHBRED,
            dateOfBirth = LocalDate.of(2025, 3, 1),
            breeder = "ノーザンファーム",
            microchipNumber = "392140000000003",
            dnaParentage = DnaParentageResult.CONSISTENT,
            registrationNumber = "2025100001",
        )
}

/**
 * 実装（[JdbcBloodHorseRepository]）へ委譲しつつ、指示されたとき save でインフラ障害を注入する テスト用デコレータ。トランザクションロールバックの検証に使う。
 */
class FailingBloodHorseRepository(private val delegate: BloodHorseRepository) :
    BloodHorseRepository {
    var failOnSave = false

    override fun findById(id: BloodHorseId): BloodHorse? = delegate.findById(id)

    override fun findAllById(ids: Set<BloodHorseId>): Map<BloodHorseId, BloodHorse> =
        delegate.findAllById(ids)

    override fun save(bloodHorse: BloodHorse): Result<BloodHorse, UpdateConflict> {
        if (failOnSave) {
            throw DataAccessResourceFailureException("インフラ障害を注入（ロールバック検証用）")
        }
        return delegate.save(bloodHorse)
    }

    override fun existsByName(name: HorseName): Boolean = delegate.existsByName(name)
}
