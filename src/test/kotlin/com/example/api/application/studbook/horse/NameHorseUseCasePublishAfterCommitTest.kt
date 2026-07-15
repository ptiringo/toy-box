package com.example.api.application.studbook.horse

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseNamed
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.unwrap
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

/**
 * ドメインイベント発行の publish-after-commit 意味論を実 PostgreSQL（Testcontainers）上で検証する統合テスト （ADR-0050 / #433）。
 *
 * 検証する意味論:
 * 1. 発行元トランザクションのコミットが確定した場合にのみ、`AFTER_COMMIT` 購読者へイベントが届くこと
 * 2. トランザクション内で発行した時点では配送されない（コミットまで遅延される）こと
 * 3. ロールバックされた場合はイベントが破棄され、購読者へ届かないこと
 *
 * 記録用購読者の Bean を足すためコンテキストキャッシュのキーは他の `@SpringBootTest` と分かれる（意図した 最小限の分岐。ロジックの網羅は
 * [NameHorseUseCaseTest] などの内側リングで済ませる）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class NameHorseUseCasePublishAfterCommitTest(
    private val nameHorse: NameHorseUseCase,
    private val bloodHorseRepository: BloodHorseRepository,
    private val horseInspectionRepository: HorseInspectionRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val recordingListener: RecordingHorseNamedListener,
    transactionManager: PlatformTransactionManager,
) : PostgresContainerSupport() {

    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val actor = Actor(AccountId(generateId()), setOf(StudbookPermissions.HORSE_NAME))

    @BeforeEach
    fun resetRecordingListener() {
        recordingListener.received.clear()
    }

    @Test
    fun `馬名登録ユースケースが成功するとコミット後に HorseNamed が購読者へ届く`() {
        val horse = persistUnnamedHorse()

        nameHorse(actor, Command(NameHorseCommand(horse.id.value, "アフターコミット"), Instant.now()))
            .unwrap()

        val event = recordingListener.received.single()
        assert(event.bloodHorseId == horse.id)
        assert(event.name.value == "アフターコミット")
    }

    @Test
    fun `トランザクション内で発行した時点では配送されずコミット確定後に届く`() {
        transactionTemplate.execute {
            eventPublisher.publishEvent(horseNamed("コミットマエ"))
            // 発行済みでもコミット前なので AFTER_COMMIT 購読者にはまだ届かない
            assert(recordingListener.received.isEmpty())
        }

        assert(recordingListener.received.single().name.value == "コミットマエ")
    }

    @Test
    fun `トランザクションがロールバックされるとイベントは購読者へ届かない`() {
        transactionTemplate.execute { status ->
            eventPublisher.publishEvent(horseNamed("ロールバック"))
            status.setRollbackOnly()
        }

        assert(recordingListener.received.isEmpty())
    }

    /** 命名対象となる未命名の軽種馬を、参照整合を保って（審査ごと）永続化する。 */
    private fun persistUnnamedHorse(): BloodHorse {
        val inspection =
            BloodHorseFixture.inspection(parentage = ParentageDetermination.NotApplicable)
        horseInspectionRepository.save(inspection)
        val horse =
            BloodHorse.createImported(
                entry = BloodHorseFixture.importedHorseEntry(),
                inspection = inspection,
                registrationNumber = PedigreeRegistrationNumber.create("2020900123").unwrap(),
            )
        return bloodHorseRepository.save(horse).unwrap()
    }

    private fun horseNamed(name: String): HorseNamed =
        HorseNamed(BloodHorseId(generateId()), HorseName.create(name).unwrap())

    /** AFTER_COMMIT で受信したイベントを記録するテスト用購読者。 */
    class RecordingHorseNamedListener {
        val received = CopyOnWriteArrayList<HorseNamed>()

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        fun onHorseNamed(event: HorseNamed) {
            received += event
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RecordingListenerConfig {
        @Bean
        fun recordingHorseNamedListener(): RecordingHorseNamedListener =
            RecordingHorseNamedListener()
    }
}
