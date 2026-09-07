package com.example.api.application.studbook

import com.example.api.application.studbook.breeding.ReportFoalingCommand
import com.example.api.application.studbook.breeding.ReportFoalingUseCase
import com.example.api.application.studbook.breeding.ReportFoalingUseCaseError
import com.example.api.application.studbook.horse.NameHorseCommand
import com.example.api.application.studbook.horse.NameHorseUseCase
import com.example.api.application.studbook.horse.NameHorseUseCaseError
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseFixture
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.infrastructure.studbook.StudbookSeeder
import com.example.api.infrastructure.studbook.breeding.BreedingRegistrationSpringDataRepository
import com.example.api.infrastructure.studbook.horse.BloodHorseSpringDataRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.example.api.support.PostgresContainerSupport
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * 楽観ロックの競合が**ユースケースのトランザクション境界を越えて**業務エラーとして返ることを実 DB で検証する（#867）。
 *
 * リポジトリ実装は競合を `Err(UpdateConflict)` に写しているが、それだけでは足りない。Spring Data JDBC の `save` が
 * 例外で競合を知らせる形のままだと、ユースケースの `@Transactional` に参加したトランザクションが例外の時点で global rollback-only をマークし、`Err`
 * を返しても**外側のコミットが `UnexpectedRollbackException` になる** （＝409 のつもりが
 * 500）。ここで検証したいのは「エラー値が呼び出し元まで届くこと」そのもの。
 *
 * 契約テスト（`Jdbc〜RepositoryContractTest`）は同じ穴をリポジトリ単体で決定的に塞ぐが、本番の境界は ユースケースの `@Transactional`
 * なので、実際のユースケース越しにも通ることをここで確かめる。
 *
 * レースは本質的に確率的なので、[CyclicBarrier] で全スレッドの発火点を揃えて窓に入りやすくしている。競合せずに 終わる回もあるが、その場合も assert
 * は成立する（レースを踏んだ回だけが追加で意味を持つ「片側検出」のテスト。 `WorldNameConcurrencyTest` と同型）。**例外が漏れていれば `Future.get()`
 * が再送出してテストが落ちる**のが要点。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class OptimisticLockConcurrencyTest(
    private val nameHorse: NameHorseUseCase,
    private val reportFoaling: ReportFoalingUseCase,
    private val bloodHorses: BloodHorseRepository,
    private val breedingResults: BreedingResultRepository,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val horseRows: BloodHorseSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
    private val jdbcClient: JdbcClient,
) : PostgresContainerSupport() {

    // WorldId は value class で lateinit を付けられないため、生 UUID を保持して都度包む
    private lateinit var worldIdValue: UUID
    private val worldId
        get() = WorldId(worldIdValue)

    private val actor
        get() = Actor(accountId = AccountId(generateId()), worldId = worldId)

    private lateinit var seeder: StudbookSeeder

    /** 基底クラスの TRUNCATE（@BeforeEach）の後に世界を作る。 */
    @BeforeEach
    fun setUpWorld() {
        worldIdValue = createWorld()
        seeder = StudbookSeeder(worldId, inspectionRows, horseRows, registrationRows, jdbcClient)
    }

    @Test
    fun `同じ馬を並行命名しても例外は漏れず負けた側は業務エラーになる`() {
        val horse = seeder.seedHorse(BloodHorseFixture.bloodHorse())

        val results =
            concurrently<Result<*, NameHorseUseCaseError>> { index ->
                nameHorse(
                    actor,
                    Command(NameHorseCommand(horse.id.value, HORSE_NAMES[index]), Instant.now()),
                )
            }

        // 勝つのは 1 スレッドだけ。負けた側は競合（窓に入った場合）か命名済み（窓の外だった場合）で、
        // どちらも業務エラーとして返る（例外が漏れていれば concurrently の Future.get() が再送出する）。
        val errors = results.mapNotNull { it.getError() }
        assert(errors.size == THREADS - 1)
        assert(
            errors.all {
                it is NameHorseUseCaseError.ConcurrentModification ||
                    it is NameHorseUseCaseError.AlreadyNamed
            }
        )
        assert(bloodHorses.findById(worldId, horse.id)?.name != null)
    }

    @Test
    fun `同じ繁殖成績へ並行報告しても例外は漏れず負けた側は業務エラーになる`() {
        val breedingResult = breedingResults.save(worldId, seededBreedingResult()).unwrap()

        val results =
            concurrently<Result<*, ReportFoalingUseCaseError>> { index ->
                reportFoaling(
                    actor,
                    Command(
                        ReportFoalingCommand(breedingResult.id.value, FOALING_OUTCOMES[index]),
                        Instant.now(),
                    ),
                )
            }

        val errors = results.mapNotNull { it.getError() }
        assert(errors.size == THREADS - 1)
        assert(
            errors.all {
                it is ReportFoalingUseCaseError.ConcurrentModification ||
                    it is ReportFoalingUseCaseError.AlreadyReported
            }
        )
        assert(breedingResults.findById(worldId, breedingResult.id)?.outcome != null)
    }

    /**
     * 全スレッドの発火点を揃えて [task] を同時に走らせ、各スレッドの結果を返す。
     *
     * `Future.get()` は本体が投げた例外を `ExecutionException` で包んで再送出する。競合が例外として漏れていれば （＝本番なら
     * 500）ここでテストが落ちる。
     */
    private fun <T> concurrently(task: (Int) -> T): List<T> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        return try {
            val tasks =
                List(THREADS) { index ->
                    Callable {
                        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        task(index)
                    }
                }
            executor.invokeAll(tasks).map { future -> future.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    /** 親（繁殖牝馬・種牡馬の登録）を seed 済みの、種付済み成績を組む（分娩結果は未報告）。 */
    private fun seededBreedingResult(): BreedingResult {
        val broodmareRegistration =
            BreedingFixture.breedingRegistration(
                broodmare = seededHorse(Sex.FEMALE, "MARE"),
                registrationNumber = "B-MARE-${generateId()}",
            )
        val stallionRegistration =
            BreedingFixture.stallionRegistration(
                stallion = seededHorse(Sex.MALE, "SIRE"),
                registrationNumber = "B-SIRE-${generateId()}",
            )
        seeder.seedRegistration(broodmareRegistration)
        seeder.seedRegistration(stallionRegistration)
        return BreedingFixture.breedingResult(
            broodmareRegistration = broodmareRegistration,
            stallionRegistration = stallionRegistration,
        )
    }

    private fun seededHorse(sex: Sex, prefix: String): BloodHorse =
        seeder.seedHorse(
            BloodHorseFixture.bloodHorse(
                sex = sex,
                registrationNumber = "$prefix-${generateId()}",
            )
        )

    private companion object {
        /** 同時に叩くスレッド数。Hikari の既定プール（10）に収まる範囲で窓に入りやすい数にする。 */
        const val THREADS = 4

        /** バリアで待ち合わせる上限。揃わないまま無言でハングさせないための保険。 */
        const val TIMEOUT_SECONDS = 10L

        /** スレッドごとに別の馬名を申請する（名前の重複ではなく楽観ロックの競合を踏ませるため）。 */
        val HORSE_NAMES = listOf("アカイナマエ", "アオイナマエ", "シロイナマエ", "クロイナマエ")

        /** スレッドごとに別の分娩結果を報告する。 */
        val FOALING_OUTCOMES =
            listOf(
                FoalingOutcome.NotConceived,
                FoalingOutcome.Abortion,
                FoalingOutcome.Stillbirth,
                FoalingOutcome.NeonatalDeath,
            )
    }
}
