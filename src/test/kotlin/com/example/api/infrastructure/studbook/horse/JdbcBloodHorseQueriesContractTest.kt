package com.example.api.infrastructure.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.infrastructure.studbook.StudbookSeeder
import com.example.api.infrastructure.studbook.breeding.BreedingRegistrationSpringDataRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 読み取りポート [com.example.api.application.studbook.horse.BloodHorseQueries] の JDBC 実装
 * [JdbcBloodHorseQueries] の契約テスト（軽量 CQRS / L2。ADR-0031）。
 *
 * 本番ターゲットの PostgreSQL（Testcontainers、[PostgresContainerSupport] で共有）に対し、 全件・id
 * 昇順・列マッピングが正しいかを検証する。行の投入は [StudbookSeeder] の row 系で行う。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class JdbcBloodHorseQueriesContractTest(
    private val jdbcClient: JdbcClient,
    private val horseRows: BloodHorseSpringDataRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
) : PostgresContainerSupport() {

    private val queries = JdbcBloodHorseQueries(jdbcClient)
    private val seeder = StudbookSeeder(inspectionRows, horseRows, registrationRows)

    @Test
    fun `登録済みの馬を全件 id 昇順で返す`() {
        // generateId() は同一ミリ秒内での単調増加を保証しない（ADR-0005 は「ほぼ単調」＝インデックス局所性が目的）
        // ため、大小が自明な固定 ID を使う。さらに挿入順を id 昇順と逆にして、ORDER BY id が効いていることを検証する。
        val smaller = UUID.fromString("00000000-0000-7000-8000-000000000001")
        val larger = UUID.fromString("00000000-0000-7000-8000-000000000002")
        seeder.seedHorseRow(id = larger, registrationNumber = "REG-002")
        seeder.seedHorseRow(id = smaller, registrationNumber = "REG-001")

        val views = queries.findAll()

        assert(views.map { it.id } == listOf(smaller, larger))
        val head = views.first()
        assert(head.registrationNumber == "REG-001")
        // seedHorseRow のデフォルト（coat_color=BAY / breed_type=THOROUGHBRED / name 未設定）を写せていること
        assert(head.coatColor == CoatColor.BAY)
        assert(head.name == null)
    }

    @Test
    fun `1頭も登録が無ければ空リストを返す`() {
        assert(queries.findAll().isEmpty())
    }
}
