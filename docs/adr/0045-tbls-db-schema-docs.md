# 0045. DB スキーマドキュメントに tbls を採用し CI でドリフトとコメント必須をゲートする

- Status: Accepted
- Date: 2026-06-30
- Deciders: Matsui

## Context（背景・課題）

Issue #447 として、DB スキーマのドキュメント整備が課題になった。現状では Flyway マイグレーション SQL が唯一のスキーマ仕様書であり、テーブル・カラムの意味・制約の説明は SQL コメントの有無に依存している。ドキュメント生成・鮮度担保の仕組みがなく、スキーマと手書きドキュメントが乖離するリスクがある。

### 検討した要素

- **ドキュメント生成ツール**: `tbls`（Golang 製 DB スキーマドキュメントジェネレータ）は live DB 接続から Markdown/JSON/Mermaid 等を出力し、`tbls diff`（生成物と現 DB のドリフト検出）・`tbls lint`（コメント必須等の規約強制）を提供する。設定ファイル `.tbls.yml` で列挙した規約を機械的に検証できる。
- **コメントの持ち場**: DB コメント（テーブル・カラムの `COMMENT ON`）をスキーマの一次情報として Flyway マイグレーション SQL に書く方針をとるか、それとも `.tbls.yml` / 別ドキュメントで管理するかという選択がある。Flyway マイグレーションは `squawk` / `sqlfluff` で lint 済み（[ADR-0032](0032-sql-lint-squawk-sqlfluff.md)）であり、`COMMENT ON` を同所に置けば「スキーマ変更＝マイグレーション」という単一の変更ルートを維持できる。
- **live DB 接続の調達**: tbls は実行時 DB 接続を必須とする。本番 DB は PostgreSQL 互換の Prisma Postgres（[ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)、#451 で H2 全面脱却済み）であり、ドキュメントも本番型 PostgreSQL で生成する。本プロジェクトは Testcontainers で PostgreSQL を起動する基盤が既にある（[ADR-0027](0027-persistence-spring-data-jdbc.md)）ため、Gradle タスクで Testcontainers を起動→tbls 実行→コンテナ停止というフローが自然に組める。
- **CI でのゲート方針**: tbls を CI で diff ゲート（生成物が最新かを検査）と lint ゲート（コメント必須規約を検査）の両面で使う。pre-commit での自動生成は CI ゲートと二重化するうえ、コンテナ起動コストが毎コミットに乗るため採用しない。
- **設定ファイルの cwd 問題**: tbls はデフォルトで cwd から `.tbls.yml` を自動探索するが、Gradle タスクの作業ディレクトリとプロジェクトルートが一致しない場合がある。そのため Gradle タスクから `--config` で絶対パスを注入して解決している。
- **#446 との方針統一**: 同時期に進む #446（terraform-docs: Terraform モジュールドキュメント生成）も「スキーマ変数定義を一次情報として Terraform ファイルに書き、生成ドキュメントは CI でドリフト検査のみ行う」方針を採った。tbls はその DB スキーマ版として方針を揃える布石となる。

### squawk の `require-timeout-settings` との整合

コメント追補マイグレーション（`COMMENT ON`）は ACCESS EXCLUSIVE ロックを取るため squawk の `require-timeout-settings` の対象になる。導入検討当初はランタイムが H2 との二重適用で `SET` 構文が使えず同ルールの除外を予定していたが、H2 全面脱却（#451 / [ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)）で前提が消えたため、**除外せずルールに準拠**する（`SET LOCAL lock_timeout / statement_timeout` をマイグレーション先頭に置く。V6 の CHECK 追加と同じパターン）。

## Decision（決定）

### ツール採用

**tbls を採用し、DB スキーマドキュメントを `dbdoc/` へ生成する**（mise 管理）。

- `./gradlew generateDbDoc`: Testcontainers で PostgreSQL を起動し tbls で `dbdoc/` を生成する（手動再生成用）。
- `./gradlew checkDbDoc`: `tbls diff`（生成物と現 DB のドリフト）と `tbls lint`（コメント必須）を検査する。CI でゲートとして実行する。

### コメントの持ち場

**テーブル・カラムのコメントはマイグレーション SQL の `COMMENT ON` で持つ**。スキーマ変更と同じ変更ルート（Flyway + squawk/sqlfluff lint）に乗せることで一次情報を分散させない。`.tbls.yml` はルール定義（どのオブジェクトにコメント必須か）を持つが、コメント文言自体は保持しない。

### 生成・検査の実行タイミング

**生成（`generateDbDoc`）は手動オンデマンド・CI でのドリフト検査は自動**とする。pre-commit での自動生成は採用しない。

- pre-commit はコンテナ起動コストが毎コミットに乗るため開発体験を損なう。
- CI の `checkDbDoc` により「`dbdoc/` が現スキーマに追従しているか」を担保する。
- `dbdoc/` はリポジトリにコミットし、PR レビューで生成物の変化を確認できるようにする。

### lint 設定の注入方法

tbls の cwd 問題を回避するため、**Gradle タスクから `--config <絶対パス>` で `.tbls.yml` を注入する**。

## Consequences（結果・影響）

- **得られるもの**: Flyway マイグレーション SQL（`COMMENT ON`）を一次情報として、`dbdoc/` に Markdown 形式のスキーマドキュメントが CI 連動で最新に保たれる。tbls lint によりコメント未記入のテーブル・カラムを CI で検出でき、ドキュメント品質を機械的に担保できる。tflint（[ADR-0017](0017-terraform-quality-gates-tflint-trivy-defer-opa.md)）/ tfctl（[ADR-0034](0034-adopt-tfctl-cli.md)）/ terraform-docs（#446）と並列のツール採否記録として ADR に残す。
- **引き受けること**: `generateDbDoc` / `checkDbDoc` は Testcontainers（PostgreSQL コンテナ）起動を伴うため実行時間がかかる。CI ゲート時間が増加するトレードオフを受け入れる（既に統合テストで Testcontainers を使用しており追加のコンテナ管理負担は限定的）。
- **関連 ADR**: [ADR-0027](0027-persistence-spring-data-jdbc.md)（Spring Data JDBC + Testcontainers 基盤）/ [ADR-0030](0030-jdbc-only-persistence-retire-inmemory.md)（JDBC 一本化 #435）/ [ADR-0032](0032-sql-lint-squawk-sqlfluff.md)（SQL lint）/ [ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)（本番 DB に Prisma Postgres を採用、PostgreSQL 互換）/ [ADR-0049](0049-decline-atlas-keep-flyway-toolchain.md)（Atlas 不採用＝本 ADR の tbls 構成を維持）。
