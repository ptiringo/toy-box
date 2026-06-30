# 0039. ブラックボックス API E2E テストに Karate を採用する

- Status: Accepted
- Date: 2026-06-30
- Deciders: Matsui

## Context（背景・課題）

controller→application→infrastructure→実 DB を実配線したまま HTTP 越しに叩く E2E が無かった。
slice テスト（@WebMvcTest）は application 層を mock するため結線そのものは検証しない。
ユースケースを「読み物としての仕様」にできるシナリオ記述を最優先（可読性/ドキュメント性）に置いた。

## Decision（決定）

- ツールは Karate（`.feature` のシナリオ記述）を採用する。座標は `io.karatelabs:karate-core` の v1.5 系。
  `karate-junit5` は JUnit Jupiter 5.x を引き込み、本 project の JUnit 6 と Platform 上で衝突しうるため、
  `Runner` API だけを使い、テスト実行は既存 JUnit 6（@SpringBootTest）に委ねる。v2（JUnit6 必須）は将来評価。
- 起動方式はインプロセス（@SpringBootTest RANDOM_PORT + Testcontainers PostgreSQL の `PostgresContainerSupport` 再利用）。
  base URL は `E2E_BASE_URL`（将来の外部 SUT）→ `karate.server.port` の順で解決し、外部エンドポイントへも向けられる。
- 専用ソースセット `src/e2eTest` + タスク `e2eTest` に隔離する。ArchUnit（src/test のみ走査）/ Kover（test タスク紐付け）/
  `check` / pre-push のいずれの対象にもしない。CI は独立ワークフロー `e2e-tests.yml` で回す。
- seed は jockey の 2 シナリオ（登録→照会の往復 / 404 ProblemDetail）。網羅・他コンテキスト・OpenAPI 突き合わせは後続 Issue。

## Consequences（結果・影響）

- 結線リグレッションを HTTP 入口から検出できる。シナリオが仕様兼ドキュメントになる。
- `.feature` + JS という異質な文法が入る。遅い E2E は内側ループ（check/pre-push）から切り離す前提を維持する。
- 「登録→照会の往復」は Create と Get-by-id が揃う jockey でのみ可能。他コンテキストへの展開は GET 整備が前提。

### フォローアップ

以下は本 ADR のスコープ外とし、フォロー issue で対応する。

- 他コンテキスト（breeding / horse 等）への横展開（往復には GET-by-id の整備が前提）
- OpenAPI スキーマと実装の突き合わせ（schema 検証）
- E2E をどこかのゲート（check / pre-push）へ昇格させるかの判断

## 関連

- [ADR-0015](0015-gradle-build-performance-tuning.md): コンテキストキャッシュ優先のテスト実行性能（E2E をゲート外に置く根拠）
- [ADR-0006](0006-kover-over-jacoco.md): Kover によるカバレッジ計測（E2E は計測対象外）
- [ADR-0027](0027-persistence-spring-data-jdbc.md): Spring Data JDBC による永続化（Testcontainers PostgreSQL 基盤を再利用）
- [ADR-0019](0019-compiler-warnings-as-errors.md): 警告のエラー化（e2eTest の Kotlin も warning-free）
