# 0056. E2E から Karate を撤退し RestTestClient で書き直す

- Status: Accepted
- Date: 2026-07-07
- Deciders: Matsui

## Context（背景・課題）

[ADR-0039](0039-e2e-api-tests-with-karate.md) でブラックボックス API E2E に Karate（`.feature` シナリオ記述）を採用したが、運用してみると用途に対して仕組みが過大だった。

- **メジャーアップで CI が壊れた**。Dependabot の `io.karatelabs:karate-core` 1.5.1 → 2.1.0 bump（[PR #561](https://github.com/ptiringo/toy-box/pull/561)）で `com.intuit.karate.Runner` とその Builder API が全面 deprecated 化し、本 project の `allWarningsAsErrors = true`（[ADR-0019](0019-compiler-warnings-as-errors.md)）により `:compileE2eTestKotlin` が警告→エラーでコンパイル不能になった。Karate v2 は JUnit 6 必須で、これを追うと Platform 上のバージョン整合の追跡コストが継続的に乗る。
- **依存の据え置きコストが恒常化していた**。ADR-0039 時点から「JUnit 衝突回避のため v1.5 系に固定し `Runner` API だけ使う」という制約を抱えており、エコシステムの更新から取り残される構造だった。
- **文法が異質で薄い**。実際のシナリオは jockey の 2 本（登録→照会の往復 / 404 ProblemDetail）のみ。この規模のために `.feature` DSL + `karate-config.js`（JS）+ `Runner` 橋渡しという Kotlin/Spring とは別系統の語彙を一式持ち込むのは、可読性の利得よりも保守面の負債が上回ると判断した。

一方、E2E テスト自体は今後拡充していく方針は変わらない。撤退させるのは Karate という**ツール**であって、実配線 E2E という**層**ではない。

代替として、本 project は既に `RestTestClient`（Spring Framework 6.2 の同期テストクライアント）を `HealthEndpointTest` / `OpenApiTest` で使っている。同じ `@SpringBootTest(RANDOM_PORT)` + Testcontainers PostgreSQL の土台に載せられ、追加依存ゼロ（`spring-boot-starter-test` 経由）で同等の HTTP 越し検証ができる。

## Decision（決定）

- **Karate を撤去する**。`io.karatelabs:karate-core` 依存、version catalog の `karate` 定義、`.feature`（`jockey.feature`）、`karate-config.js`、`Runner` を使う E2E ランナーを削除する。
- **E2E は素の Spring ネイティブ（`RestTestClient`）で書き直す**。`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` + `PostgresContainerSupport`（Testcontainers PostgreSQL）でアプリを実 port 起動し、`RestTestClient` でシナリオを流す。既存の jockey 2 シナリオ（404 problem+json / 登録→照会の往復）はそのまま移植する。CLAUDE.md の「アプリ全体の統合テスト」規約（`RestTestClient`）と一致し、`HealthEndpointTest` と同型になる。
- **隔離構成は維持する**。専用ソースセット `src/e2eTest` + タスク `e2eTest` はそのまま残し、ArchUnit / Kover / `check` / pre-push のいずれの対象にもしない（探索的で遅い E2E を内側ループから切り離す前提は不変）。CI は独立ワークフロー `e2e-tests.yml` で回す。今後の E2E 拡充はこの器の上で行う。
- **API 結合テストは E2E 層に一本化する**。Karate 由来の別ソースセット（かつて存在した `integrationTest`）は設けず、実配線の API テストは `e2eTest` に集約する。
- **CI ワークフロー / ドキュメントの Karate 前提を一般化する**。`e2e-tests.yml` のジョブ名 `E2E (Karate)` → `E2E`、`.claude/rules/testing.md` の E2E 節を RestTestClient 前提へ更新する。Dependabot PR #561 は Karate 撤去に伴い不要となるためクローズする。

## Consequences（結果・影響）

- **CI が Karate のバージョン追従から解放される**。JUnit 整合の据え置き制約が消え、`allWarningsAsErrors` 下でも E2E がエコシステム更新で壊れなくなる。テスト基盤の語彙が Kotlin/Spring に一本化され、認知負荷が下がる。
- **シナリオの「読み物性」は下がる**。`.feature` の宣言的な仕様記述という ADR-0039 が最重視した利点は失う。ただし現状の 2 シナリオでは利得が薄く、`RestTestClient` の Kotlin コードでも十分読める（日本語テストケース名 + jsonPath アサーション）と判断した。往復シナリオの生成 ID の受け渡しは jsonPath で取り出す。
- 追加依存はなく、`RestTestClient` は `spring-boot-starter-test`（`e2eTest` が `testImplementation` を継承）で供給される。
- ADR-0039 の「往復は Create + Get-by-id が揃う jockey でのみ可能」「他コンテキストへの展開は GET 整備が前提」という制約は移植後もそのまま引き継ぐ（ツール差し替えであり適用範囲は変えない）。

## 関連

- [ADR-0039](0039-e2e-api-tests-with-karate.md): 本 ADR が supersede する（Karate 採用の決定）
- [ADR-0019](0019-compiler-warnings-as-errors.md): 警告のエラー化（Karate v2 deprecation が CI を壊した機序）
- [ADR-0027](0027-persistence-spring-data-jdbc.md): Spring Data JDBC 永続化（Testcontainers PostgreSQL 基盤を継続再利用）
- [ADR-0015](0015-gradle-build-performance-tuning.md): テスト実行性能（E2E をゲート外に置く根拠は不変）
- [ADR-0006](0006-kover-over-jacoco.md): Kover カバレッジ（E2E は計測対象外のまま）
