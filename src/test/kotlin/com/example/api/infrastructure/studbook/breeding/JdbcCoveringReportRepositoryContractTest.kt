package com.example.api.infrastructure.studbook.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.CoveringReportId
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcCoveringReportRepositoryContractTest(
    private val rows: CoveringReportSpringDataRepository
) : PostgresContainerSupport() {

    private val repository = JdbcCoveringReportRepository(rows)

    @BeforeEach
    fun cleanUp() {
        rows.deleteAll()
    }

    @Test
    fun `新規の種付成績報告はversionがnullなのでinsertされ属性ごと往復できる`() {
        val report = BreedingFixture.coveringReport(submittedOn = LocalDate.of(2024, 10, 1))

        val saved = repository.save(report).unwrap()
        val found = repository.findById(report.id)

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
        val report = BreedingFixture.coveringReport() // coveringYear=2024
        repository.save(report).unwrap()

        val found =
            repository.findByStallionRegistrationIdAndCoveringYear(
                report.stallionRegistrationId,
                Year.of(2024),
            )
        assert(found != null)
        assert(found!!.id == report.id)
        // 別の年は引き当たらない
        assert(
            repository.findByStallionRegistrationIdAndCoveringYear(
                report.stallionRegistrationId,
                Year.of(2099),
            ) == null
        )
    }

    @Test
    fun `同一種牡馬×同一種付年の二重insertはUNIQUE制約で拒否される`() {
        // ドメインサービスの一意性検証をすり抜ける read-then-insert 並行競合（#532）の backstop。
        val stallionRegistration = BreedingFixture.stallionRegistration()
        repository
            .save(BreedingFixture.coveringReport(stallionRegistration = stallionRegistration))
            .unwrap()
        val duplicate = BreedingFixture.coveringReport(stallionRegistration = stallionRegistration)

        assertThrows<DataIntegrityViolationException> { repository.save(duplicate) }
    }

    @Test
    fun `並行削除された保存済み集約のsaveはUpdateConflictを返す`() {
        val inserted = repository.save(BreedingFixture.coveringReport()).unwrap()
        rows.deleteAll()

        // version 非 null の集約の save は update 経路になり、対象行が無いので競合として検出される
        val conflicted = repository.save(inserted)

        assert(conflicted.getError() == UpdateConflict)
    }

    @Test
    fun `存在しないIDのfindByIdはnullを返す`() {
        assert(repository.findById(CoveringReportId(generateId())) == null)
    }
}
