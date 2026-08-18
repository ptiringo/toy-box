# GitHub Copilot 向けリポジトリ指示

## このファイルの位置づけ

**規約の出所はこのファイルではない。** 詳細を再掲せず、出所へのリンクで辿らせる。

| 対象 | 出所 |
| --- | --- |
| 全体方針・開発コマンド・ツール管理 | [CLAUDE.md](../CLAUDE.md) |
| 領域別の規約（アーキテクチャ / テスト / API 設計 / エラー / マイグレーション / Terraform 等） | [.claude/rules/](../.claude/rules/) |
| 決定の経緯 | [docs/adr/](../docs/adr/) |
| ツールとそのバージョン | [mise.toml](../mise.toml) |
| Git フックの内容 | [lefthook.yml](../lefthook.yml) |
| セットアップ手順 | [README.md](../README.md) |

ここに手順やバージョンを書き写すと二重管理になり、出所が更新されても追従せず乖離する（実際に
lefthook の手動導入手順が `mise.toml` と食い違ったまま残っていた。#776）。**このファイルを増やす前に、
出所側へ書いてここからリンクできないかを先に検討すること。**

## 言語とスタイル

- **コメント・ドキュメントは日本語**、**識別子（変数・関数・クラス名）は英語**で書く。
- **コミットメッセージは日本語・Conventional Commits 形式**（`feat: 新機能を追加`）。commit-msg フックが検査する。
- 命名は Kotlin 標準に従う（ktfmt / detekt が強制する）。

## セットアップ

```bash
mise bootstrap      # mise.toml 記載のツールを導入し、続けて Git フック（lefthook）を有効化
mise list           # 導入済みツールの確認
```

**ツールを手動で導入しない**（`apt` / `wget` / 個別のインストーラ）。バージョンの出所は `mise.toml` 一つで、
手動導入は手元と CI で挙動が食い違う原因になる。新しいツールが要るときは `mise.toml` に足す。

## アーキテクチャ（最重要）

**Spring MVC + Virtual Thread を採用し、リアクティブ流派は採らない**（[ADR-0002](../docs/adr/0002-virtual-thread-over-reactive.md)）。

- **WebFlux / Reactor / coroutine を導入しない。** `suspend` / `Mono` / `Flux` を使ったコードを提案しない。
  ブロッキング IO は Virtual Thread 上で走るので、**同期コードで素直に書く**。
- 永続化は **Spring Data JDBC + PostgreSQL**（R2DBC ではない。[ADR-0027](../docs/adr/0027-persistence-spring-data-jdbc.md) /
  [ADR-0030](../docs/adr/0030-jdbc-only-persistence-retire-inmemory.md)）。
- オニオンアーキテクチャの 4 リング（domainModel / domainService / applicationService / adapter）と
  境界づけられたコンテキストで構成する。各リングの責務・依存方向・パターン（Value Object / Entity /
  Command / Domain Event / 軽量 CQRS）は [.claude/rules/architecture.md](../.claude/rules/architecture.md) が出所。
- **これらの規約は ArchUnit + jMolecules と detekt カスタムルールで機械強制されており、違反すると
  `./gradlew check` が落ちる。**

### ディレクトリ

| パス | 内容 |
| --- | --- |
| `src/main/kotlin/com/example/api/` | API 本体（`domain` / `application` / `controller` / `infrastructure` / `mcp` / `config`） |
| `src/main/resources/db/migration/` | Flyway マイグレーション（規約は [.claude/rules/migrations.md](../.claude/rules/migrations.md)） |
| `detekt-rules/` | プロジェクト固有の detekt カスタムルール |
| `frontend/` | Vite + React の SPA |
| `infra/` | Terraform 構成（規約は [.claude/rules/terraform.md](../.claude/rules/terraform.md)） |

## ビルドと検証

```bash
./gradlew build
./gradlew test
./gradlew check     # ktfmt + detekt + test + koverVerifyMature を一括
./gradlew bootRun
```

**Kotlin の変更は `test` ではなく `check` で締める。** ArchUnit の規約テスト・detekt カスタムルール・
カバレッジゲートは focused なテスト実行では走らない。

## テスト

記法も戦略も [.claude/rules/testing.md](../.claude/rules/testing.md) が出所。要点だけ:

- **JUnit 5**（`org.junit.jupiter.api.Test`）を使う。`kotlin.test.Test` は使わない。
- **アサーションは Kotlin 標準の `assert` 関数**（Power Assert が式を分解して表示する）。
- **Controller の slice テストは `@WebMvcTest` + `MockMvcTester`**（`@WebFluxTest` / `WebTestClient` ではない）。
- **モックは applicationService の Repository ポート境界に限る。** ドメイン層は Fixture で実物を組む。
- テストケース名は日本語で意図を表す。
- DB を要する層は Testcontainers（PostgreSQL）を使う。

## Git フック（lefthook）

内容の出所は [lefthook.yml](../lefthook.yml)。**個々のコマンド名はそちらを見る**（ここで一覧すると増減に追従できない）。

- **pre-commit**: シークレット検査・EditorConfig・各種 lint / フォーマットチェック（Kotlin / frontend /
  Terraform / SQL / シェル / ワークフロー / 設定ファイル）。対象ファイルを含まないコミットでは該当フックがスキップされる。
- **pre-push**: 全テスト。Testcontainers を使うため Docker を要求し、到達できなければテスト起動前に
  理由つきで落とす（[ADR-0071](../docs/adr/0071-pre-push-docker-fail-fast-guard.md)）。
- **commit-msg**: Conventional Commits 形式の検査（マージコミットは除外）。

```bash
lefthook run pre-commit                 # 手動で一括実行
LEFTHOOK_EXCLUDE=<name> git commit      # 個別フックのスキップ（<name> は lefthook.yml のコマンド名）
```

フックが動かないときは `mise bootstrap`（または `lefthook install`）で張り直す。

## フォーマット

`.editorconfig` が出所（`end_of_line = lf` / `insert_final_newline = true` /
`trim_trailing_whitespace = true` / `charset = utf-8`。`*.md` は行末空白の削除が無効）。
editorconfig-checker が pre-commit と CI の両方で強制する。

Kotlin は ktfmt、設定ファイル（TOML / JSON / YAML）は dprint、frontend は Biome が整形する。

## CI/CD（GitHub Actions）

- **アクションは完全なコミット SHA に固定する**（タグ参照にしない）。
- **ジョブには `timeout-minutes` を必ず指定する**。
- ワークフローは actionlint（lint）と zizmor（セキュリティ監査）が pre-commit と CI で検査する。

## セキュリティ

- **シークレットをコミットしない**（gitleaks が pre-commit で検査する）。ローカル開発のシークレットは
  平文で export せず 1Password + fnox 経由で渡す（[ADR-0004](../docs/adr/0004-secrets-fnox-1password.md)）。
- **API キー・パスワードをハードコードしない。機密情報をログへ出さない。**
- SQL はパラメータ化クエリを使う。外部入力は検証する（エラー描画の規約は
  [.claude/rules/error-handling.md](../.claude/rules/error-handling.md)）。
- **GitHub 操作は MCP ではなく `gh` CLI で行う**（[ADR-0001](../docs/adr/0001-drop-github-mcp-use-gh-cli.md)）。
