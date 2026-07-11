package com.example.api.replay

import com.example.api.replay.fixture.FixtureLoader
import com.example.api.support.PostgresContainerSupport
import com.example.api.support.deleteAllStudbookTables
import java.nio.file.Path
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BreedingReplayTest(private val engine: ReplayEngine, private val jdbcClient: JdbcClient) :
    PostgresContainerSupport() {

    @BeforeEach fun cleanUp() = deleteAllStudbookTables(jdbcClient)

    @Test
    fun `正常系の実在馬は繁殖サイクルを最後まで一周する`() {
        val outcome = engine.run(FixtureLoader.load("01-normal.json"))
        assert(outcome.stoppedAt == null) {
            "想定外の停止: ${outcome.stoppedAt} / ${outcome.stopReason} / steps=${outcome.steps}"
        }
        assert(outcome.steps.all { it.ok })
        assert(outcome.steps.map { it.step }.contains(ReplayStep.SUBMIT_BREEDING_REPORT))
    }

    @Test
    fun `全フィクスチャを流して突合レポートを書き出す`() {
        val outcomes = FixtureLoader.loadAll().map(engine::run)
        val report = ReconciliationReport.render(outcomes)
        assert(report.contains("# 繁殖 replay 突合レポート"))
        assert(outcomes.isNotEmpty())
        ReconciliationReport.write(
            outcomes,
            Path.of("build", "reports", "replay", "reconciliation.md"),
        )
    }
}
