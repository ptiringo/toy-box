package com.example.api.support

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test

/**
 * クラス間並列が**実際に効いている**ことを守るゲート（#690 / ADR-0079）。
 *
 * 並列化は `build.gradle.kts` の `junit.jupiter.execution.parallel.*` という設定 3 行に乗っており、消えても
 * テストは全部緑のまま通る。速度が静かに戻るだけで誰も気づかないため、ここで能動的に検出する。
 *
 * 2 つのテストクラスが互いの到達を待ち合わせる。**クラス間並列が効いていれば即座に両方が揃い、逐次実行なら
 * 必ずタイムアウトして落ちる**。壁時計を測る方式と違い測定ノイズに左右されず、設定の有無だけを判定できる。
 *
 * 落ちたときに疑うのは次の順:
 * 1. `build.gradle.kts` の `parallel.enabled` / `parallel.mode.classes.default` が消えていないか
 * 2. これらのクラスに `@Execution(SAME_THREAD)` が付いていないか（本クラスは並列側に居る必要がある）
 * 3. 実行環境の CPU が 1 コアで、そもそも並列度が 1 になっていないか
 */
private object ParallelExecutionProbe {
    val classA = CountDownLatch(1)
    val classB = CountDownLatch(1)

    /** 並列なら即座に揃うので、待つのは「効いていない」と判定するまでの猶予にすぎない。 */
    const val TIMEOUT_SECONDS = 10L

    const val FAILURE_MESSAGE =
        "クラス間並列が効いていない（build.gradle.kts の junit.jupiter.execution.parallel 設定を確認すること）"
}

class ParallelExecutionProbeATest {
    @Test
    fun `別クラスのテストと同時に走っていること`() {
        ParallelExecutionProbe.classA.countDown()
        val met =
            ParallelExecutionProbe.classB.await(
                ParallelExecutionProbe.TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
        check(met) { ParallelExecutionProbe.FAILURE_MESSAGE }
    }
}

class ParallelExecutionProbeBTest {
    @Test
    fun `別クラスのテストと同時に走っていること`() {
        ParallelExecutionProbe.classB.countDown()
        val met =
            ParallelExecutionProbe.classA.await(
                ParallelExecutionProbe.TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
        check(met) { ParallelExecutionProbe.FAILURE_MESSAGE }
    }
}
