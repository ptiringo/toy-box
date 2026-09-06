package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.CoveringReport
import com.example.api.domain.studbook.model.breeding.CoveringReportId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.infrastructure.studbook.StudbookSeeder
import com.example.api.infrastructure.studbook.horse.BloodHorseSpringDataRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * ドメインポート CoveringReportRepository の Spring Data JDBC 実装 [JdbcCoveringReportRepository] の契約テスト
 * （#540。BreedingResult の契約テストと同型）。
 *
 * 本番ターゲットと同じ PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対して検証する。
 *
 * 検証する契約:
 * 1. value class ID・Year を、永続化モデル分離＋手書きマッパーで橋渡しして往復できること
 * 2. 外部採番（UUIDv7）で `@Id` が常に非 null でも、`@Version` が null のとき insert と判定されること
 * 3. `findByStallionRegistrationIdAndCoveringYear` が種牡馬×種付年で引き当てられること
 * 4. 「種牡馬×種付年」の一意性が UNIQUE 制約でスキーマ側にも強制されること（read-then-insert 競合の backstop）
 * 5. 並行削除された保存済み集約への save が UpdateConflict を返すこと（楽観ロックの契約）
 * 6. update 経路が呼び出し側のトランザクションを要求し、その境界の内側で競合しても外側のコミットが通ること（#867）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcCoveringReportRepositoryContractTest(
    private val rows: CoveringReportSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val horseRows: BloodHorseSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
    private val jdbcClient: JdbcClient,
    transactionManager: PlatformTransactionManager,
) : PostgresContainerSupport() {

    /** 呼び出し側のトランザクション境界を張る（#867）。本番ではユースケースの `@Transactional` が担う。 */
    private val transaction = TransactionTemplate(transactionManager)

    private val repository = JdbcCoveringReportRepository(rows, jdbcClient)
    // WorldId は value class で lateinit を付けられないため、生 UUID を保持して都度包む
    private lateinit var worldIdValue: UUID
    private val worldId
        get() = WorldId(worldIdValue)

    private lateinit var seeder: StudbookSeeder

    /** 基底クラスの TRUNCATE（@BeforeEach）の後に世界を作る必要があるため、フィールド初期化ではなくここで組む。 */
    @BeforeEach
    fun setUpWorld() {
        worldIdValue = createWorld()
        seeder = StudbookSeeder(worldId, inspectionRows, horseRows, registrationRows, jdbcClient)
    }

    /** 親（種牡馬とその繁殖登録）を seed 済みの繁殖登録を返す。 */
    private fun seededStallionRegistration(): BreedingRegistration {
        val stallion = seeder.seedHorse(BloodHorseFixture.bloodHorse(sex = Sex.MALE))
        return seeder.seedRegistration(BreedingFixture.stallionRegistration(stallion = stallion))
    }

    @Test
    fun `新規の種付成績報告はversionがnullなのでinsertされ属性ごと往復できる`() {
        val report =
            BreedingFixture.coveringReport(
                stallionRegistration = seededStallionRegistration(),
                submittedOn = LocalDate.of(2024, 10, 1),
            )

        val saved = repository.save(worldId, report).unwrap()
        val found = repository.findById(worldId, report.id)

        assert(saved.version != null)
        assert(found != null)
        assert(found!!.id == report.id)
        assert(found.stallionRegistrationId == report.stallionRegistrationId)
        assert(found.coveringYear == Year.of(2024))
        assert(found.submittedOn == LocalDate.of(2024, 10, 1))
        // 期限（2024-09-30）超過の導出が復元後も機能する
        assert(found.submittedLate)
    }

    @Test
    fun `findByStallionRegistrationIdAndCoveringYearで種牡馬と年から引き当てられる`() {
        val report =
            BreedingFixture.coveringReport(
                stallionRegistration = seededStallionRegistration()
            ) // coveringYear=2024
        repository.save(worldId, report).unwrap()

        val found =
            repository.findByStallionRegistrationIdAndCoveringYear(
                worldId,
                report.stallionRegistrationId,
                Year.of(2024),
            )
        assert(found != null)
        assert(found!!.id == report.id)
        // 別の年は引き当たらない
        assert(
            repository.findByStallionRegistrationIdAndCoveringYear(
                worldId,
                report.stallionRegistrationId,
                Year.of(2099),
            ) == null
        )
    }

    @Test
    fun `同一種牡馬×同一種付年の二重insertはUNIQUE制約で拒否される`() {
        // ドメインサービスの一意性検証をすり抜ける read-then-insert 並行競合（#532）の backstop。
        val stallionRegistration = seededStallionRegistration()
        repository
            .save(
                worldId,
                BreedingFixture.coveringReport(stallionRegistration = stallionRegistration),
            )
            .unwrap()
        val duplicate = BreedingFixture.coveringReport(stallionRegistration = stallionRegistration)

        assertThrows<DataIntegrityViolationException> { repository.save(worldId, duplicate) }
    }

    @Test
    fun `並行削除された保存済み集約のsaveはUpdateConflictを返す`() {
        val inserted = seededCoveringReport()
        rows.deleteAll()

        // version 非 null の集約の save は update 経路になり、対象行が無いので競合として検出される。
        // 内側で例外が起きると rollback-only がマークされて外側のコミットが落ちるため、境界ごと通す（#867）。
        val conflicted = inTransaction { repository.save(worldId, inserted) }

        assert(conflicted.getError() == UpdateConflict)
    }

    @Test
    fun `トランザクションの外から更新すると落ちる`() {
        // 行ロックはトランザクションの終了まで保持されて初めて競合判定の前提になる。境界の外から呼ばれると
        // FOR UPDATE が即座に解放されて防御が無症状で消えるため、例外で顕在化させる（#867）。
        val inserted = seededCoveringReport()

        assertThrows<IllegalStateException> { repository.save(worldId, inserted) }
    }

    /** 種牡馬の繁殖登録ごと種付成績報告を 1 件永続化して返す（update 経路の検証の下ごしらえ）。 */
    private fun seededCoveringReport(): CoveringReport =
        repository
            .save(
                worldId,
                BreedingFixture.coveringReport(stallionRegistration = seededStallionRegistration()),
            )
            .unwrap()

    /** [block] を 1 つのトランザクションで包み、**コミットまで**通す（コミットが落ちればテストも落ちる。#867）。 */
    private fun <T : Any> inTransaction(block: () -> T): T =
        checkNotNull(transaction.execute { block() })

    @Test
    fun `存在しない繁殖登録を参照する行はFK制約で拒否される`() {
        val orphan =
            CoveringReportRow(
                worldId = worldId.value,
                id = generateId(),
                stallionBreedingRegistrationId = generateId(),
                coveringYear = 2024,
                submittedOn = LocalDate.of(2024, 9, 30),
                version = null,
            )

        assertThrows<DataIntegrityViolationException> { rows.save(orphan) }
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(worldId, CoveringReportId(generateId())) == null)
    }
}
