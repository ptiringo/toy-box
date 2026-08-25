package com.example.api.architecture.fixture

import com.example.api.controller.HelloController
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest

/**
 * `TestParallelismRulesTest.webMvcTestsRunInSameThread` が**実際に違反を検出する**ことを確かめるための違反サンプル （ゲートの非空振り規約
 * `.claude/rules/gates.md`）。
 *
 * 禁じたい形は 2 つあり、どちらも「`@WebMvcTest` が並列で走ってしまう」という同じ結果になる。
 * 1. `@Execution` を付け忘れる（[ParallelUnsafeWebMvcTestFixture]）
 * 2. `@Execution(CONCURRENT)` を明示する（[ConcurrentWebMvcTestFixture]）
 *
 * 本番ルールは走査時にこの fixture パッケージを除外するので、ここに置いてあってもゲートは緑のまま保たれる。
 *
 * どちらもテストメソッド（`@Test`）を持たないため、テストランナーは実行しない（Spring コンテキストも起動しない）。
 */
@WebMvcTest(HelloController::class) class ParallelUnsafeWebMvcTestFixture

/** `@Execution` は付いているが値が `CONCURRENT` で、結局並列に走ってしまう違反サンプル。 */
@WebMvcTest(HelloController::class)
@Execution(ExecutionMode.CONCURRENT)
class ConcurrentWebMvcTestFixture
