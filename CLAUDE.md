# CLAUDE.md

## プロジェクト概要

Kotlin Spring Boot (Spring MVC + Virtual Thread) の API プロジェクト。複数のドメインモデル（軽種馬登録・競馬、エンターテイメント、テニス）を探索する sandbox として開発している。

Virtual Thread (`spring.threads.virtual.enabled=true`) を有効化し、ブロッキング JDBC 等の同期 IO を素直に書きながらスレッド占有を避ける（ビルド toolchain は JDK 25）。WebFlux / Reactor / coroutine ベースのリアクティブ流派は採らない（[ADR-0002](docs/adr/0002-virtual-thread-over-reactive.md)）。

## 開発コマンド

```bash
./gradlew build
./gradlew test
./gradlew check     # ktfmt + detekt + test + koverVerifyMature を一括
./gradlew bootRun
```

- ツールバージョンは mise で管理する（`mise.toml`）。対話型シェル（`mise activate` 済み）と Claude Code セッション（`.claude/hooks/session-start-mise.sh` が `mise hook-env` を適用）では PATH に通るため直接実行できる。通っていない非対話シェルでのみ `mise exec -- <command>` を挟む。
- **Kotlin の変更は `test` ではなく `check` で締める**。ArchUnit の規約テスト・detekt カスタムルール・カバレッジゲート（`koverVerifyMature`）は focused なテスト実行では走らない。単一テストの実行方法を含め、詳細は `.claude/rules/testing.md`。
- Kotlin 以外の lint / 生成（sqlfluff・squawk・dprint・shellcheck・vacuum・tbls）は lefthook の pre-commit と CI が自動で走らせる。手で叩きたいときの出所は `lefthook.yml` と `.github/workflows/`。
- ktfmt がフォーマッタ、detekt が静的解析（設定は `config/detekt/detekt.yml`、レポートは `build/reports/detekt/`）。プロジェクト固有のカスタムルール（例: ドメイン / アプリケーション層で `throw` しない）は `:detekt-rules` モジュールで定義する（`.claude/rules/architecture.md`）。依存の更新追従は Dependabot、設定ファイル（TOML/JSON/YAML）の整形は dprint（[ADR-0063](docs/adr/0063-dprint-config-file-formatting.md)）が担う。ビルド構成の健全性を機械強制する常設ツール（nebula.lint / dependency-analysis 等）は不採用（[ADR-0057](docs/adr/0057-gradle-build-health-tooling-not-adopted.md)）。

## アーキテクチャ

**Spring MVC の標準的な `@RestController` パターン**を採用する。Controller が HTTP エンドポイントを定義し、戻り値のオブジェクトを Jackson が JSON にシリアライズする。ドメインはフレームワーク非依存のピュアなモデルに保つ。リクエスト処理は Virtual Thread 上で走るため、原則 **同期コードで書く**（`suspend` / `Mono` / `Flux` を導入しない）。

オニオンアーキテクチャの 4 リング（domainModel / domainService / applicationService / adapter）構成。`domain` 配下は境界づけられたコンテキスト（`studbook` / `racing` / `sakamichi` / `tennis`）ごとに `model/` と `service/` へ分割し、adapter は `controller`（REST）/ `infrastructure`（永続化）/ `mcp` の 3 つ。**コンテキスト間の依存は層・リングをまたぐ場合も含めて禁止**（`domain.shared` は共有カーネルで対象外）。ドメインモデルには [jMolecules](https://github.com/xmolecules/jmolecules) のアノテーション（`@AggregateRoot` / `@ValueObject` / `@field:Identity` / `@Repository` / `@DomainEvent` 等）で DDD ビルディングブロックの役割を表明する。

各リングの責務・依存方向・Spring 依存可否、パッケージ構成、各パターン（Value Object は `@JvmInline value class`、Entity は ID 同一性＋自己検証ファクトリ＋イミュータブル、Command／Domain Event の封筒、軽量 CQRS の Query / Read Model）の規約とコード例は **`.claude/rules/architecture.md`**（`.kt` 編集時にロード）に集約している。規約は ArchUnit + jMolecules（`src/test/kotlin/com/example/api/architecture/`）と detekt カスタムルールで機械的に強制され、違反すると `./gradlew check` が落ちる。

決定経緯は ADR を参照: ID 生成 [ADR-0005](docs/adr/0005-time-based-uuid-generation.md) / イミュータブル集約 [ADR-0009](docs/adr/0009-immutable-aggregates.md) / 自己検証ファクトリ [ADR-0014](docs/adr/0014-self-validating-factory-over-confinement.md) / ドメインイベント [ADR-0029](docs/adr/0029-domain-events-via-state-transition-return.md) / イベント発行（publish-after-commit）[ADR-0050](docs/adr/0050-domain-event-publication-after-commit.md) / 軽量 CQRS [ADR-0031](docs/adr/0031-lightweight-cqrs-read-model.md) / MCP アダプタ [ADR-0035](docs/adr/0035-mcp-interface-adapter.md)。

## 永続化

Spring Data JDBC + PostgreSQL。集約と永続化 Row は別型に分けマッパーで写す（[ADR-0027](docs/adr/0027-persistence-spring-data-jdbc.md)）。InMemory 実装は持たず **JDBC 一本化**（[ADR-0030](docs/adr/0030-jdbc-only-persistence-retire-inmemory.md)）、本番 DB は Prisma Postgres（[ADR-0044](docs/adr/0044-adopt-prisma-postgres-for-production-db.md)）、スキーマはコンテキスト別に分ける（[ADR-0048](docs/adr/0048-per-context-db-schema-namespaces.md)）。集約 ⇔ テーブルの写し方は `.claude/rules/architecture.md`、Flyway マイグレーションの規約は **`.claude/rules/migrations.md`**（`db/migration` 編集時にロード）。

DB スキーマドキュメントは tbls が生成する（[ADR-0045](docs/adr/0045-tbls-db-schema-docs.md)）。`./gradlew generateDbDoc` で `dbdoc/` を再生成し、`./gradlew checkDbDoc` が鮮度とコメント必須を検査する。

## コーディング規約

- **コメントとドキュメント**は日本語、**識別子（変数・関数・クラス名）**は英語で書く。命名は Kotlin 標準（ktfmt / detekt が強制する）。
- **コミットメッセージ**: 日本語・Conventional Commits 形式。ヘッダー（例: `feat: 新機能を追加`）の後に、ファイルごとの詳細な変更内容を書く。
- **PR のマージ方式**: 必ず **merge commit**（`gh pr merge --merge`）を使う。squash / rebase は使わない（個々のコミット履歴を main に残す方針）。CLI でマージする場合はセルフ PR の BLOCKED 表示回避のため `--admin` を付ける。
- **テスト**: 戦略（リング × テスト手法・カバレッジゲート）も記法（JUnit 5 / Power Assert / `@WebMvcTest` / 日本語ケース名）も **`.claude/rules/testing.md`**（`src/test` 編集時にロード）に集約している。
- **フォーマット**: `.editorconfig`（LF・末尾改行・行末空白削除・UTF-8）が出所で、editorconfig-checker が pre-commit と CI で強制する。

## Claude 指示ファイル・スキルの記述方針

`CLAUDE.md` / `.claude/rules/` / `.claude/skills/` など **Claude への指示ファイルはリポジトリ管理（クローンすれば誰でも同じ構成になる共有物）**である。したがって **環境依存の内容を書かず、ポータブルに保つ**。

- **書かない例**: 個人マシンの sandbox 設定・許可ホスト・絶対パス・`PATH`、特定セッションのメモリへの参照（`[[...]]`）、自分の環境でしか成り立たない前提手順。
- **環境依存の設定そのもの**は各自のローカル設定（`.claude/settings.local.json` 等）や各自のメモリに置き、共有ファイルからは出所をリンクで指すに留める。
- **手順は self-contained に**書く。「なぜ・何を」を本文で完結させ、特定環境固有の前提（「この環境では X が PATH に無い」等）は一般化した表現にする。
- **常時ロードの footprint を小さく保つ**: 特定作業時だけ要る規約は `.claude/rules/*.md` の `paths:` frontmatter で path-scope 化し、該当ファイルを触るときだけロードさせる。解決済みの経緯は ADR（`/adr` スキル）、手順はスキルへ逃がす。指示ファイルの棚卸しは `/tidy-memory`。

## 優先度管理

Issue の優先度は **GitHub Projects（`toy-box` = Project #4）の `Priority` single-select カスタムフィールド**で管理する（`P1: 今すぐ` / `P2: 近いうち` / `P3: いずれ` / `P4: 探索・保留` の 4 段階）。優先度ラベルは廃止済みで、出所はこのフィールド 1 つ（[ADR-0011](docs/adr/0011-priority-via-projects-custom-field.md)）。

- **作成した Issue は必ず Project（#4）に追加する**。Project に入れた Issue だけが `Priority` を持てるため、「Project へ追加 → `Priority` 設定」までを 1 セットで行う（優先度が即決でなくても Project には入れ、未定なら後から設定する）。
- **Issue の操作は `/issue-ops` スキルに集約**。次にやる Issue を選ぶ・一覧する・新規作成する・優先度を変える手順はそこにある。とくに候補選びは、`gh project item-list` がクローズ済みも含むため priority だけで拾うと実装済みの Issue を「未対応」として提案してしまう（再発実績あり）。

## ツール管理

- **mise**: セットアップは `mise install`、確認は `mise list`。管理対象のツール一覧は `mise.toml` が出所（ビルド用 JDK、各種 lint / 解析、tbls、lefthook、fnox、terraform / tfctl、kotlin-lsp 等）。JDK のバージョン要件は `build.gradle.kts` の Gradle toolchain で宣言し、実体は mise が供給する（toolchain auto-detection が `JAVA_HOME` / `PATH` 経由で検出する）。
  - **`mise.lock` は生成したホストの platform 分しか記録しない**。macOS で生成した lock のまま Linux CI で `--locked` すると解決できず落ちるので、必要な platform のエントリを揃える（`http` backend は `mise lock` が sha を永続化しないため、公式の SHA256SUMS から手で追記する）。
  - **CI で `mise-action` の `install_args` にツールを列挙するときは `mise.toml` の `[tools]` キー（backend 修飾名）と完全一致させる**。短縮名では `--locked` が解決できず落ちる。ローカルの `--dry-run` は既インストール済みだと見逃す。
- **Lefthook**: Git フックは `lefthook.yml` が出所（セットアップは `lefthook install`）。pre-commit で各種 lint、pre-push で全テスト、commit-msg で Conventional Commits 形式を検査する。フック全体の手動実行は `lefthook run pre-commit`、個別スキップは `LEFTHOOK_EXCLUDE=<name> git commit`。

## MCP サーバー設定

必要な MCP は各自が `/plugin` 等でアドホックに入れず、**リポジトリ管理の設定ファイルに宣言**して共有する（[ADR-0003](docs/adr/0003-consolidate-mcp-config-in-repo.md)）。Claude Code 用は `.mcp.json`（採用は `context7`＝ライブラリ最新ドキュメント参照 / `terraform`＝レジストリ・プロバイダ参照）、VS Code・Copilot 用は `.vscode/mcp.json`（別フォーマットで併存。共通 MCP は両者を同期する）。`.mcp.json` を唯一の出所とし、グローバル設定や `/plugin` で同名サーバーを二重定義しない。

同じ方針で、**Claude Code の LSP プラグインは `.claude/settings.json` の `enabledPlugins` に宣言**する（`kotlin-lsp@claude-plugins-official`。要求バイナリは mise が供給。LSP はゲート（detekt / ArchUnit / `check`）を置き換えない補助。[ADR-0046](docs/adr/0046-adopt-kotlin-lsp-plugin.md)）。

**GitHub 操作は MCP ではなく `gh` CLI で行う**（[ADR-0001](docs/adr/0001-drop-github-mcp-use-gh-cli.md)）。

## シークレット管理（fnox + 1Password）

ローカル開発のシークレットは平文で shell profile に `export` せず、**1Password に保管＋必要時だけ `fnox exec -- <command>` で env へ展開**する（`fnox.toml` は `op://` 参照のみで秘密を含まずコミット可。現状 `[secrets]` は空）。選定経緯は [ADR-0004](docs/adr/0004-secrets-fnox-1password.md)、運用手順・前提セットアップは **`.claude/rules/secrets.md`**（`fnox.toml` / `mise.toml` 編集時にロード）。

## API（OpenAPI・エラー・Actuator）

`springdoc-openapi` で API ドキュメントを自動生成する（Swagger UI: `/swagger-ui.html`、OpenAPI JSON: `/v3/api-docs`）。全体定義 `@OpenAPIDefinition`（タイトル・タグ等）は `ApiApplication.kt` に置く。仕様の品質（summary 記述漏れ・operationId 命名等）は vacuum の lint で CI ゲートする（[ADR-0054](docs/adr/0054-vacuum-openapi-lint.md)）。リソース設計（Google AIP 準拠）とエラー描画（RFC 9457）の規約は **`.claude/rules/api-design.md`** / **`.claude/rules/error-handling.md`**（`controller` / `application` / `domain` 編集時にロード）。

ヘルスチェックは `/actuator/health` のみ公開する（`info` / `metrics` 等は非公開。Cloud Run のヘルスチェックが使う）。

## 認証（OAuth2 リソースサーバ）

認証は GCP Identity Platform に委譲し、この API は **OAuth2 リソースサーバとして ID トークン（JWT）を検証するだけ**とする（資格情報を保持しない）。設定は `SecurityConfig`（**`controller` パッケージ**に置く。RFC 9457 の `problem()` ビルダが adapter リングにあり、内側から参照するとオニオン規約に反するため）と `application.yml` の `spring.security.oauth2.resourceserver.jwt.issuer-uri` / `.audiences`。issuer が OIDC discovery を公開しているため `JwtDecoder` は自前で書かない。決定経緯は [ADR-0064](docs/adr/0064-authn-via-identity-platform-authz-in-app.md)。

- **`permitAll` は運用・CI が壊れるエンドポイントに限る**: `/actuator/health`（Cloud Run のヘルスチェック）、`/v3/api-docs` 配下と Swagger UI（`generateOpenApiDocs` が forked bootRun 経由で取得するため、認証を掛けると OpenAPI lint のゲートが壊れる）、MCP エンドポイント（クライアントがトークンを持てない）。それ以外は `authenticated`。
- **認可（何をしてよいか）はフィルタ層で判断しない**。ロール・権限の出所は自前 DB で、認可は application 層が担う。
- 本番（Cloud Run）は `GCP_PROJECT_ID` を環境変数で受け取る（`deploy.yml`）。注入されないと issuer が既定値に落ち、全トークンが 401 になる。

## Google Cloud 操作のガードレール

Claude Code から GCP を触るときは、変更・削除・課金を伴う操作を deny（CI/HCP 専用）/ ask（確認強制）で抑え、ローカルは最小権限 viewer SA の impersonation で読み取りに限定する。強制の実体は `.claude/settings.json` の permissions で、語彙・手順は `.claude/rules/gcp-guardrails.md`、決定経緯は [ADR-0036](docs/adr/0036-gcp-operation-guardrails.md)。**変更系は正規ルート（アプリ deploy は GitHub Actions、infra apply は HCP Terraform run）に寄せる**。

## インフラストラクチャ（Terraform）

`infra/` に Terraform 構成（HCP Terraform バックエンド、cicd / cloudrun モジュール）を管理する。ディレクトリ構成・モジュール・コマンドの詳細は **`.claude/rules/terraform.md`**（`infra/**` 編集時にロード）。
