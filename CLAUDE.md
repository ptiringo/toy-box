# CLAUDE.md

## プロジェクト概要

Kotlin Spring Boot (Spring MVC + Virtual Thread) を使用した API プロジェクトです。複数のドメインモデル（競馬、エンターテイメント、テニス）を探索する sandbox プロジェクトとして開発されています。

JDK 21 で正式化された Virtual Thread (`spring.threads.virtual.enabled=true`) を有効化することで、ブロッキング JDBC 等の同期 IO を素直に書きながらスレッド占有を避ける構成を採用しています（ビルド toolchain は JDK 25）。WebFlux / Reactor / coroutine ベースのリアクティブ流派ではありません。

## 開発コマンド

### mise によるツールバージョン管理

ツールバージョンは mise で管理する（`mise.toml`、ツール一覧と導入手順は後述「ツール管理」）。対話型シェル（`mise activate` 済み）と Claude Code セッション（`.claude/hooks/session-start-mise.sh` が `mise hook-env` を適用）では mise 管理ツールが PATH に通るため、コマンドを直接実行できる。

```bash
./gradlew build              # 通常（mise activate 済み / Claude Code セッション）
mise exec -- ./gradlew build # mise 未活性化の非対話シェルのみ
```

### ビルドとテスト

```bash
# ビルド
./gradlew build

# テスト実行
./gradlew test

# アプリケーション起動
./gradlew bootRun
```

### コード品質チェック

```bash
# ktfmt フォーマットチェック / 自動フォーマット
./gradlew ktfmtCheck
./gradlew ktfmtFormat

# detekt 静的解析（命名規則・コードスメル・複雑度等の検出）
./gradlew detekt

# 全チェック実行（ktfmtCheck + detekt + test 等を含む）
./gradlew check

# SQL（Flyway マイグレーション）の lint
mise exec -- sqlfluff lint src/main/resources/db/migration   # 書式・スタイル
mise exec -- sqlfluff fix src/main/resources/db/migration    # 自動整形
mise exec -- squawk src/main/resources/db/migration/*.sql    # マイグレーション安全性

# dprint による設定ファイル（TOML/JSON/YAML）の整形チェック / 自動整形
mise exec -- dprint check
mise exec -- dprint fmt

# シェルスクリプトの lint（ShellCheck）
mise exec -- shellcheck .claude/hooks/*.sh .claude/hooks/lib/*.sh .devcontainer/*.sh scripts/*.sh

# DB スキーマドキュメント（tbls）の生成・検査
./gradlew generateDbDoc   # dbdoc/ を再生成（手動）
./gradlew checkDbDoc      # tbls diff（鮮度）+ tbls lint（コメント必須）を検査

# OpenAPI 仕様の生成・lint（vacuum。CI 専用ゲート、ローカル実行には Docker が必要）
./gradlew generateOpenApiDocs   # build/openapi.json を書き出す（forked bootRun + docker-compose）
./gradlew lintOpenApiDocs       # 生成 + vacuum lint（ルールは config/vacuum/ruleset.yaml）
```

ktfmt はフォーマッタ、detekt は静的解析ツール。detekt 設定は `config/detekt/detekt.yml`（`buildUponDefaultConfig = true` でデフォルトに上書き、雛形再生成は `./gradlew detektGenerateConfig`）、レポートは `build/reports/detekt/`。プロジェクト固有のカスタムルール（例: ドメイン / アプリケーション層で `throw` しない）は `:detekt-rules` モジュールで定義し `detektPlugins` で組み込む（詳細は `.claude/rules/architecture.md`）。detekt はソースコードの静的解析を担い、依存の更新追従は Dependabot が担う。ビルド構成の健全性（未使用依存の棚卸し等）を機械強制する常設ツール（nebula.lint / dependency-analysis 等）は現時点では不採用（実機評価の結果、Kotlin DSL 非対応や Spring Boot での偽陽性で費用対効果が合わず。[ADR-0057](docs/adr/0057-gradle-build-health-tooling-not-adopted.md)）。

設定ファイル（TOML/JSON/YAML）の整形は dprint が担う（`json`/`toml`/`pretty_yaml` プラグイン、コメント保持、Node 非依存）。採否は [ADR-0063](docs/adr/0063-dprint-config-file-formatting.md)。

### 単一テストの実行

```bash
# 特定のテストクラスを実行
./gradlew test --tests "HelloControllerTest"

# 特定のテストメソッドを実行（メソッド名に応じてパターンを調整してください）
./gradlew test --tests "HelloControllerTest.*hello*"
```

## アーキテクチャ

### 構成パターン

**Spring MVC の標準的な `@RestController` パターン**を採用する。Controller（`controller/`）が `@RestController` + `@GetMapping` 等で HTTP エンドポイントを定義し、戻り値のオブジェクトをそのまま返すと Jackson が JSON にシリアライズする。Domain（`domain/`）はフレームワーク非依存のピュアなモデル。

リクエスト処理スレッドは Virtual Thread 上で走るため、ブロッキング IO（JDBC 等）を呼び出しても OS スレッドを占有しない。`suspend` / `Mono` / `Flux` を導入する動機が薄いので、原則 **同期コードで書く**。リアクティブ流派を採らない経緯は [ADR-0002](docs/adr/0002-virtual-thread-over-reactive.md) を参照。

### アーキテクチャテスト（ArchUnit）

アーキテクチャ規約は ArchUnit + jMolecules で機械的に強制されています（`src/test/kotlin/com/example/api/architecture/` 配下に関心ごとへ分割した `〜RulesTest` 群）。レイヤー依存方向（オニオン）、境界づけられたコンテキスト間の分離、DDD ビルディングブロックの整合性などが `./gradlew test` で検証されます。規約の詳細は `.claude/rules/architecture.md` を参照してください。

### ドメイン駆動設計

ドメインモデルには [jMolecules](https://github.com/xmolecules/jmolecules) のアノテーション（`@AggregateRoot` / `@ValueObject` / `@field:Identity` / `@Repository` / `@DomainEvent` 等）で DDD ビルディングブロックの役割を表明し、整合性は ArchUnit（`JMoleculesDddRules`）で検証する。各パターン（Value Object は `@JvmInline value class`、Entity は ID 同一性＋自己検証ファクトリ＋イミュータブル、Command／Domain Event の封筒、軽量 CQRS の Query / Read Model）の規約とコード例は **`.claude/rules/architecture.md`**（`.kt` 編集時にロード）に集約している。決定経緯は ADR を参照: ID 生成 [ADR-0005](docs/adr/0005-time-based-uuid-generation.md) / イミュータブル集約 [ADR-0009](docs/adr/0009-immutable-aggregates.md) / 自己検証ファクトリ [ADR-0014](docs/adr/0014-self-validating-factory-over-confinement.md) / ドメインイベント [ADR-0029](docs/adr/0029-domain-events-via-state-transition-return.md) / イベント発行（publish-after-commit）[ADR-0050](docs/adr/0050-domain-event-publication-after-commit.md) / 軽量 CQRS [ADR-0031](docs/adr/0031-lightweight-cqrs-read-model.md)。

### パッケージ構成

オニオンアーキテクチャの 4 リング（domainModel / domainService / applicationService / adapter）構成。`domain` 配下は各コンテキストを `model/` と `service/` に分割する。

```
com.example.api/
├── ApiApplication.kt    # エントリーポイント（@OpenAPIDefinition もここ）
├── controller/          # adapter (rest): @RestController（HTTP エンドポイント）
├── application/         # applicationService: ユースケース
│   ├── studbook/
│   └── racing/
├── domain/              # ドメイン（フレームワーク非依存）
│   ├── shared/          # 共有カーネル（Command / Entity 基底）。全コンテキストから参照可
│   ├── studbook/        # 軽種馬登録コンテキスト（JAIRS: 血統登録・繁殖登録）
│   │   ├── model/       #   domainModel: Entity / VO / Repository ポート
│   │   └── service/     #   domainService: トップレベル関数のドメインロジック
│   ├── racing/          # 競馬コンテキスト（JRA: 騎手・競走）
│   │   ├── model/
│   │   └── service/
│   ├── sakamichi/model/ # エンターテイメントコンテキスト
│   └── tennis/model/    # スポーツコンテキスト
└── infrastructure/      # adapter (persistence): ポートの具象実装（Spring 依存可）
    ├── studbook/
    └── racing/
```

各リング（domain.shared / domain.\*.model / domain.\*.service / application / controller / infrastructure）の責務・依存方向・Spring 依存可否は ArchUnit で強制する。**詳細は `.claude/rules/architecture.md`**（`.kt` 編集時にロード）。要点のみ: ユースケース関数は `動詞 + リソース名`、入力 DTO は `〜Command`（書き込み系）／ `〜Query`（読み取り系）サフィックス。`studbook` / `racing` / `sakamichi` / `tennis` は境界づけられたコンテキストで、コンテキスト間の依存は層・リングをまたぐ場合も含めて禁止（`domain.shared` は共有カーネルで対象外）。

## コーディング規約

### 言語とスタイル

- **コメントとドキュメント**: 日本語で記述
- **変数名、関数名、クラス名**: 英語で記述（意味を明確に）
- **コミットメッセージ**: 日本語で記述、Conventional Commits 形式に準拠。最初に Conventional Commits ヘッダー（例: `feat: 新機能を追加`）を記述し、その後ファイルごとの詳細な変更内容を記述
- **PR のマージ方式**: 必ず **merge commit**（`gh pr merge --merge`）を使う。squash / rebase は使わない（個々のコミット履歴を main に残す方針）。CLI でマージする場合はセルフ PR の BLOCKED 表示回避のため `--admin` を付ける

### 命名規則

- **クラス名**: PascalCase（例: `UserService`, `OrderController`）
- **関数名**: camelCase（例: `createUser`, `validateInput`）
- **定数**: UPPER_SNAKE_CASE（例: `MAX_RETRY_COUNT`）
- **プロパティ**: camelCase（例: `userId`, `emailAddress`）

### テスト規約

テスト戦略（オニオン各リング × テストピラミッドの対応、どの層で何を・どうテストするか）とカバレッジハーネス（Kover の `total` / `mature` 2 variant 構成・成熟領域のみゲートするラチェット運用）は `.claude/rules/testing.md` を参照。本節は個別の記法（アノテーション・アサーション・命名）を定める。

#### アノテーション

- **JUnit 5 を使用**: `org.junit.jupiter.api.Test` アノテーションを使用
- **kotlin.test.Test は使用禁止**: マルチプラットフォーム対応が不要なため

#### アサーション

- **Kotlin の `assert` 関数を優先**: 単体テストでは Power Assert を活用
- **コントローラーの slice テスト**: `@WebMvcTest` + `MockMvcTester` を使用
- **アプリ全体の統合テスト**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` + `RestTestClient` を使用（`RestTestClient` は Spring Framework 6.2 で追加された `RestClient` ベースの sync 版テストクライアント）
- **テストケース命名**: 日本語でテストの意図を明確に表現

実例は `HelloControllerTest` / `BloodHorseControllerTest`（slice）を参照。

### EditorConfig 準拠

- `end_of_line = lf`: Unix 形式の改行コード
- `insert_final_newline = true`: ファイル末尾に必ず改行
- `trim_trailing_whitespace = true`: 行末空白を削除（マークダウンを除く）
- `charset = utf-8`: UTF-8 エンコーディング

## Claude 指示ファイル・スキルの記述方針

`CLAUDE.md` / `.claude/rules/` / `.claude/skills/` など **Claude への指示ファイルはリポジトリ管理（クローンすれば誰でも同じ構成になる共有物）**である。したがって **環境依存の内容を書かず、ポータブルに保つ**。

- **書かない例**: 個人マシンの sandbox 設定・許可ホスト・絶対パス・`PATH`、特定セッションのメモリへの参照（`[[...]]`）、自分の環境でしか成り立たない前提手順。
- **環境依存の設定そのもの**は各自のローカル設定（`.claude/settings.local.json` 等）や各自のメモリに置き、共有ファイルからは出所をリンクで指すに留める。
- **手順は self-contained に**書く。「なぜ・何を」を本文で完結させ、特定環境固有の前提（「この環境では X が PATH に無い」等）は一般化した表現にする。
- **常時ロードの footprint を小さく保つ**: 特定作業時だけ要る規約は `.claude/rules/*.md` の `paths:` frontmatter で path-scope 化し、該当ファイルを触るときだけロードさせる（例: `architecture.md`＝`.kt`、`testing.md`＝`src/test`、`terraform.md`＝`infra/**`、`secrets.md`＝`fnox.toml`/`mise.toml`）。解決済みの経緯は ADR、手順はスキルへ逃がす。

## 優先度管理

Issue の優先度は **GitHub Projects（`toy-box` = Project #4）の `Priority` single-select カスタムフィールド**で管理する。優先度ラベル（`P1〜P4`）は廃止済み（採否の経緯は [ADR-0011](docs/adr/0011-priority-via-projects-custom-field.md)）。

- **出所は Project の Priority フィールド 1 つ**。ラベルとの併用はしない（二重管理を避ける）。
- オプションは `P1: 今すぐ` / `P2: 近いうち` / `P3: いずれ` / `P4: 探索・保留` の 4 段階。
- **作成した Issue は必ず Project（#4）に追加する**。Project に入れた Issue だけが `Priority` フィールドを持てるため、issue を立てたら「Project へ追加 → `Priority` 設定」までを 1 セットで行う（優先度が即決でなくても Project には必ず入れ、未定なら後から設定する）。
- 優先度順に眺める・束ねる・絞るときは Project のビューを使う（`gh issue list` のラベル列には優先度は出ない）。
- **Issue の操作は `/issue-ops` スキルに集約**。次にやる Issue を選ぶ・候補を一覧する／新規作成して Project 追加＋優先度設定／優先度変更の手順（jq・操作 ID・実装済み確認）はそこを使う。とくに候補選びは、`gh project item-list` がクローズ済みも含み出力に open/closed が無いため priority だけで拾うとクローズ済み・実装済みを「未対応」として提案してしまう（再発実績あり）。`.status != "Done"` で絞る手順がスキルにある。
- フィールド定義と既存ラベルからの移行はスクリプト化してある（`scripts/migrate-priority-to-project.sh`、`gh project` CLI で再現可能）。Project スコープが要るので事前に `gh auth refresh -s project`。

## ツール管理

### mise

セットアップは `mise install`、確認は `mise list`（未導入なら [mise インストール手順](https://mise.jdx.dev/getting-started.html)）。管理ツール（詳細は `mise.toml`）: ビルド用 JDK（`java` Temurin 25）、lint / 解析（`actionlint` / `editorconfig-checker` / `shellcheck` / `sqlfluff` / `squawk` / `vacuum`＝OpenAPI lint / `zizmor` / `gitleaks` / `dprint`＝設定ファイル整形）、DB スキーマドキュメント生成（`tbls`。採否は [ADR-0045](docs/adr/0045-tbls-db-schema-docs.md)）、Git フック（`lefthook`）、シークレット（`fnox`）、インフラ（`terraform` と HCP Terraform 操作 CLI の `tfctl`。tfctl の採否は [ADR-0034](docs/adr/0034-adopt-tfctl-cli.md)）、コードインテリジェンス（`kotlin-lsp`＝Claude Code の Kotlin LSP プラグインが要求する JetBrains 公式 Language Server。採否と供給方法は [ADR-0046](docs/adr/0046-adopt-kotlin-lsp-plugin.md)）。

**Java バージョン管理について**: JDK のバージョン要件は `build.gradle.kts` の Gradle toolchain で宣言（`languageVersion = 25`）。実体の JDK は mise が提供し、Gradle の toolchain auto-detection が `JAVA_HOME` / `PATH` 経由で検出する。

### Lefthook

Git フックは **Lefthook** で管理（`lefthook.yml`）。セットアップは `lefthook install`。

- **pre-commit**（並列）: gitleaks、EditorConfig チェック、ktfmt チェック、detekt、actionlint、zizmor、Terraform fmt / validate、sqlfluff / squawk による SQL チェック、ShellCheck によるシェルスクリプトチェック
- **pre-push**: 全テスト
- **commit-msg**: Conventional Commits 形式チェック（マージ進行中は検査せず通すため、main 取り込みマージに `--no-verify` は不要）

```bash
lefthook run pre-commit                                # フック全体を手動実行
LEFTHOOK_EXCLUDE=ktfmt-check git commit -m "メッセージ"   # 特定コマンドをスキップ
```

## MCP サーバー設定

必要な MCP は各自が `/plugin` 等でアドホックに入れず、**リポジトリ管理の設定ファイルに宣言**して共有する（クローンすれば同一構成・再現性が保たれる。経緯は [ADR-0003](docs/adr/0003-consolidate-mcp-config-in-repo.md)）。Claude Code 用は `.mcp.json`（リポジトリ root。採用は `context7`＝ライブラリ最新ドキュメント参照 / `terraform`＝レジストリ・プロバイダ参照）、VS Code・Copilot 用は `.vscode/mcp.json`（別フォーマット・別ファイルで併存。共通 MCP（例 `context7`）は両者を同期）。`.mcp.json` を唯一の出所とし、グローバル設定や `/plugin` で同名サーバーを二重定義しない（初回はクローン後の承認プロンプトに応じる。承認状態は各自の `~/.claude.json`）。

同じ「リポジトリ管理で宣言・共有」の方針で、**Claude Code の LSP プラグインは `.claude/settings.json` の `enabledPlugins` に宣言**する（現状 `kotlin-lsp@claude-plugins-official`＝Kotlin の編集時診断・コードナビ）。要求バイナリ `kotlin-lsp`（JetBrains 公式）は mise 管理で供給する（`mise install`）。LSP はゲート（detekt / ArchUnit / gradle check）を置き換えない補助で、採否と供給方法は [ADR-0046](docs/adr/0046-adopt-kotlin-lsp-plugin.md)。

**GitHub 操作は MCP ではなく `gh` CLI で行う**（[ADR-0001](docs/adr/0001-drop-github-mcp-use-gh-cli.md)）。サンドボックス下の TLS 問題（`OSStatus -26276`）は、`gh` を `.claude/settings.local.json` の `sandbox.excludedCommands` に `"gh"` と `"gh *"` の両方で登録して sandbox 外で実行し回避する。複合コマンド（`A && gh ...`）はマッチしないので `gh` は単体コマンドで実行する。

## シークレット管理（fnox + 1Password）

ローカル開発のシークレットは平文で shell profile に `export` せず、**1Password に保管＋必要時だけ `fnox exec -- <command>` で env へ展開**する（仕組みは mise 管理の [fnox](https://fnox.jdx.dev/)。`fnox.toml` は `op://` 参照のみで秘密を含まず git にコミット可）。**現状、定義しているシークレットはない**（`fnox.toml` の `[secrets]` は空。将来の GCP 認証情報等に備えて維持）。選定経緯は [ADR-0004](docs/adr/0004-secrets-fnox-1password.md)、運用手順・前提セットアップは **`.claude/rules/secrets.md`**（`fnox.toml` / `mise.toml` 編集時にロード）。

## OpenAPI/Swagger

`springdoc-openapi` で API ドキュメントを自動生成する（Swagger UI: `/swagger-ui.html`、OpenAPI JSON: `/v3/api-docs`）。コントローラーのメソッドに `@Operation` / `@ApiResponse` / `@Content` 等を付与してドキュメント化し、全体定義 `@OpenAPIDefinition`（タイトル・タグ等）は `ApiApplication.kt` に置く。

仕様の品質（summary 記述漏れ・operationId 命名等）は vacuum による lint で CI ゲートする（`openapi-lint.yml`、ルールは `config/vacuum/ruleset.yaml`、意図的な除外は `config/vacuum/ignore.yaml`。採否は [ADR-0054](docs/adr/0054-vacuum-openapi-lint.md)）。`OpenApiTest` は配線スモークとして残す。

## Spring Boot Actuator

ヘルスチェックは `/actuator/health` で公開（公開設定は `application.yml`、詳細表示は認可時のみ、動作確認は `HealthEndpointTest.kt`）。アプリ起動後に `curl http://localhost:8080/actuator/health` で `{"status":"UP"}` が返る。公開しているのはヘルスエンドポイントのみで、`info` / `metrics` 等は非公開。

## Google Cloud 操作のガードレール

Claude Code から GCP を触るときは、変更・削除・課金を伴う操作を deny（CI/HCP 専用）/ ask（確認強制）で抑え、ローカルは最小権限 viewer SA の impersonation で読み取りに限定する。語彙・手順は `.claude/rules/gcp-guardrails.md`、決定経緯は [ADR-0036](docs/adr/0036-gcp-operation-guardrails.md) を参照。

## インフラストラクチャ（Terraform）

`infra/` に Terraform 構成（HCP Terraform バックエンド、cicd / cloudrun モジュール）を管理する。ディレクトリ構成・モジュール・コマンドの詳細は **`.claude/rules/terraform.md`**（`infra/**` 編集時にロード）。

## 注意事項

- このプロジェクトは現在、永続化層（データベース、リポジトリ）を持ちません
- ドメインモデルは探索的な実装であり、TODO コメントが含まれています
- コード品質を重視しており、CI で ktfmt / detekt / EditorConfig のチェックが自動実行されます
