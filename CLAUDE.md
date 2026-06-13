# CLAUDE.md

## プロジェクト概要

Kotlin Spring Boot (Spring MVC + Virtual Thread) を使用した API プロジェクトです。複数のドメインモデル（競馬、エンターテイメント、テニス）を探索する sandbox プロジェクトとして開発されています。

JDK 21 の Virtual Thread (`spring.threads.virtual.enabled=true`) を有効化することで、ブロッキング JDBC 等の同期 IO を素直に書きながらスレッド占有を避ける構成を採用しています。WebFlux / Reactor / coroutine ベースのリアクティブ流派ではありません。

## 開発コマンド

### mise によるツールバージョン管理

このプロジェクトでは mise を使用してツールバージョンを管理しています。以下の環境では mise 管理下のツールが PATH に通った状態でコマンドを直接実行できます。

- **対話型シェル**: `mise activate` 済みの場合（`~/.zshrc` 等で設定済みなら自動）
- **Claude Code**: `.claude/hooks/session-start-mise.sh` が SessionStart 時に `mise hook-env` を適用するため、自動で mise 管理ツールが利用可能

mise が活性化されていない非対話シェルから実行する場合のみ、`mise exec --` プレフィックスを付けてください。

```bash
# 通常はこちら（mise activate 済み / Claude Code セッション）
./gradlew build

# mise が活性化されていない環境のみ
mise exec -- ./gradlew build
```

> いずれの環境でも mise 自体のインストールが前提です。未導入の場合は [mise インストール手順](https://mise.jdx.dev/getting-started.html) を参照のうえ、`mise install` で `mise.toml` 指定のツール一式をセットアップしてください。

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
# ktfmt フォーマットチェック
./gradlew ktfmtCheck

# ktfmt 自動フォーマット
./gradlew ktfmtFormat

# detekt 静的解析（命名規則・コードスメル・複雑度等の検出）
./gradlew detekt

# 全チェック実行（ktfmtCheck + detekt + test 等を含む）
./gradlew check
```

ktfmt はフォーマッタ、detekt は静的解析ツールという役割分担です。detekt の設定は `config/detekt/detekt.yml` にあり、`buildUponDefaultConfig = true` でデフォルト設定に上書きする形で運用しています。雛形を再生成したい場合は `./gradlew detektGenerateConfig` を実行してください。レポートは `build/reports/detekt/` に HTML / SARIF / Checkstyle XML / Markdown 形式で出力されます。

### 単一テストの実行

```bash
# 特定のテストクラスを実行
./gradlew test --tests "HelloControllerTest"

# 特定のテストメソッドを実行（メソッド名に応じてパターンを調整してください）
./gradlew test --tests "HelloControllerTest.*hello*"
```

## アーキテクチャ

### 構成パターン

このプロジェクトは **Spring MVC の標準的な `@RestController` パターン** を採用しています：

- **Controller** (`controller/`): `@RestController` + `@GetMapping` 等で HTTP エンドポイントを定義
- **Domain** (`domain/`): フレームワークに依存しないピュアなドメインモデル

戻り値はオブジェクトをそのまま返却し、Jackson が JSON にシリアライズします。

例：
```kotlin
// HelloController.kt
@RestController
class HelloController {
    data class HelloResponse(val message: String)

    @GetMapping("/api/hello")
    fun hello(): HelloResponse = HelloResponse("Hello World")
}
```

リクエスト処理スレッドは Virtual Thread 上で走るため、内部でブロッキング IO（JDBC 等）を呼び出しても OS スレッドは占有されません。`suspend` / `Mono` / `Flux` を導入する動機が薄いので、原則 **同期コードで書く** ことを推奨します。

### アーキテクチャテスト（ArchUnit）

アーキテクチャ規約は ArchUnit + jMolecules で機械的に強制されています（`src/test/kotlin/com/example/api/architecture/ArchitectureTest.kt`）。レイヤー依存方向（オニオン）、境界づけられたコンテキスト間の分離、DDD ビルディングブロックの整合性などが `./gradlew test` で検証されます。規約の詳細は `.claude/rules/architecture.md` を参照してください。

### ドメイン駆動設計

ドメインモデルには [jMolecules](https://github.com/xmolecules/jmolecules) のアノテーション（`org.jmolecules.ddd.annotation.*`）で DDD ビルディングブロックとしての役割を表明します。整合性は ArchUnit（`JMoleculesDddRules`）で検証されます。

#### Value Object パターン

型安全性のために `@JvmInline value class` を使用し、`@ValueObject` で役割を表明：

```kotlin
@ValueObject @JvmInline value class JockeyId(val value: UUID)

@ValueObject @JvmInline value class BloodHorseId(val value: UUID)
```

ゼロコスト抽象化により、異なるエンティティの ID を誤って混同することを防ぎます。他の集約への参照はこの ID 値クラスを介して行います（集約オブジェクトの直接参照は ArchUnit で違反として検出されます）。

#### Entity パターン

エンティティは UUID ベースの同一性を持ちます：

- **ID による等価性**: `equals()` と `hashCode()` は ID のみで実装
- **UUID 生成戦略**: タイムベース（UUIDv7 相当の `Generators.timeBasedEpochRandomGenerator()`、`java-uuid-generator` ライブラリ使用）に統一する。生成値が時刻順にソート可能で永続化時のインデックス局所性に優れるため、ランダム（`UUID.randomUUID()`）ではなくこちらを標準とする。生成ロジックは `domain.shared.generateId()` に集約しており、各 ID 値クラスは `JockeyId(generateId())` のようにこの関数を介して生成する（エンティティごとに生成方法を書き分けない）
- **役割の表明**: 集約ルートには `@AggregateRoot`、識別子プロパティには `@field:Identity` を付与（`@Identity` は FIELD ターゲットのため use-site target の明示が必要）

#### Command パターン

`Command<T>` でドメインコマンドをラップ：

```kotlin
class Command<T>(
    val t: T,
    val timestamp: LocalDateTime,
)

// 使用例
fun registerInStudBook(command: Command<StudBook>)
```

### パッケージ構成

オニオンアーキテクチャの 4 リング（domainModel / domainService / applicationService / adapter）構成。`domain` 配下は各コンテキストを `model/` と `service/` に分割する。

```
com.example.api/
├── ApiApplication.kt    # エントリーポイント（@OpenAPIDefinition もここ）
├── controller/          # adapter (rest): @RestController（HTTP エンドポイント）
├── application/         # applicationService: ユースケース
│   └── horseracing/
├── domain/              # ドメイン（フレームワーク非依存）
│   ├── shared/          # 共有カーネル（Command / Entity 基底）。全コンテキストから参照可
│   ├── horseracing/     # 競馬コンテキスト
│   │   ├── model/       #   domainModel: Entity / VO / Repository ポート
│   │   └── service/     #   domainService: トップレベル関数のドメインロジック
│   ├── sakamichi/model/ # エンターテイメントコンテキスト
│   └── tennis/model/    # スポーツコンテキスト
└── infrastructure/      # adapter (persistence): ポートの具象実装（Spring 依存可）
    └── horseracing/
```

**設計原則**（ArchUnit で強制。詳細は `.claude/rules/architecture.md`）:

- **domain.shared**: 共有カーネル。`Command` / `Entity` 基底など、コンテキスト横断の最小限の基盤のみ
- **domain.\*.model**: Entity / Value Object / Repository ポート（interface）/ 集約内で完結するロジック。Repository ポートには jMolecules の `@Repository` を付与
- **domain.\*.service**: 複数の集約をまたぐドメインロジック。**Kotlin のトップレベル関数で書き**、`service/` への配置でドメインサービスと表現する（jMolecules `@Service` は付けない）。モデルにのみ依存でき、その逆は禁止
- **application**: ユースケース（コマンド DTO + ユースケースクラス + そのユースケースに紐づく失敗バリアント）。ドメインを組み合わせて業務シナリオを構築する。ユースケースを DI 経由で公開するための `@Service` / `@Component` のみ Spring 依存を許容し、ロジック本体は Plain Kotlin で書く
- **controller**: HTTP アダプター。`Result` から `ResponseEntity` への変換のみを担う
- **infrastructure**: Repository ポートの具体実装、外部システム連携などのアダプター。Spring 依存可

ユースケース関数の命名は `動詞 + リソース名` を基本とし、入力 DTO は `〜Command`（書き込み系）／ `〜Query`（読み取り系）サフィックスを付ける。

`domain` / `application` / `infrastructure` 配下のコンテキスト（`horseracing` / `sakamichi` / `tennis`）は境界づけられたコンテキストであり、コンテキスト間の依存は層やリングをまたぐ場合も含めて禁止（ArchUnit で強制）。`domain.shared` は対象外。

## コーディング規約

### 言語とスタイル

- **コメントとドキュメント**: 日本語で記述
- **変数名、関数名、クラス名**: 英語で記述（意味を明確に）
- **コミットメッセージ**: 日本語で記述、Conventional Commits 形式に準拠。最初に Conventional Commits ヘッダー（例: `feat: 新機能を追加`）を記述し、その後ファイルごとの詳細な変更内容を記述

### 命名規則

- **クラス名**: PascalCase（例: `UserService`, `OrderController`）
- **関数名**: camelCase（例: `createUser`, `validateInput`）
- **定数**: UPPER_SNAKE_CASE（例: `MAX_RETRY_COUNT`）
- **プロパティ**: camelCase（例: `userId`, `emailAddress`）

### テスト規約

#### アノテーション

- **JUnit 5 を使用**: `org.junit.jupiter.api.Test` アノテーションを使用
- **kotlin.test.Test は使用禁止**: マルチプラットフォーム対応が不要なため

#### アサーション

- **Kotlin の `assert` 関数を優先**: 単体テストでは Power Assert を活用
- **コントローラーの slice テスト**: `@WebMvcTest` + `MockMvcTester` を使用
- **アプリ全体の統合テスト**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` + `RestTestClient` を使用（`RestTestClient` は Spring Framework 6.2 で追加された `RestClient` ベースの sync 版テストクライアント）
- **テストケース命名**: 日本語でテストの意図を明確に表現

例（slice テスト）：
```kotlin
@WebMvcTest(HelloController::class)
class HelloControllerTest(val mockMvc: MockMvc) {
    private val tester = MockMvcTester.create(mockMvc)

    @Test
    fun `helloエンドポイントがHello Worldを返すこと`() {
        tester.get().uri("/api/hello")
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.message")
            .isEqualTo("Hello World")
    }
}
```

### EditorConfig 準拠

- `end_of_line = lf`: Unix 形式の改行コード
- `insert_final_newline = true`: ファイル末尾に必ず改行
- `trim_trailing_whitespace = true`: 行末空白を削除（マークダウンを除く）
- `charset = utf-8`: UTF-8 エンコーディング

## ツール管理

### mise

プロジェクトツールは **mise** で管理されています（`mise.toml` 参照）：

```bash
# ツールのインストール
mise install

# インストール済みツールの確認
mise list
```

現在管理されているツール（`mise.toml` 参照）：
- `actionlint`: GitHub Actions ワークフローの lint
- `editorconfig-checker`: EditorConfig 準拠チェック
- `fnox`: シークレット管理（暗号化保管／外部参照＋必要時だけ env へ展開）
- `gitleaks`: シークレット混入スキャン
- `java` (Temurin 21): Gradle / Kotlin のビルドおよびテスト実行用 JDK
- `lefthook`: Git フック管理
- `terraform`: インフラ構成管理
- `zizmor`: GitHub Actions ワークフローのセキュリティ監査

**Java バージョン管理について**: JDK のバージョン要件は `build.gradle.kts` の Gradle toolchain で宣言しています（`languageVersion = 21`）。実体の JDK は mise が提供し、Gradle の toolchain auto-detection が `JAVA_HOME` / `PATH` 経由で検出します。

### Lefthook

Git フックは **Lefthook** で管理されています（`lefthook.yml` 参照）。

#### セットアップ

```bash
lefthook install
```

#### 実行されるフック

- **pre-commit**（並列実行）: gitleaks シークレットスキャン、EditorConfig チェック、ktfmt チェック、detekt 静的解析、actionlint、zizmor、Terraform fmt チェック、Terraform validate
- **pre-push**: 全テスト実行
- **commit-msg**: Conventional Commits 形式のチェック

#### フックの手動実行

```bash
# pre-commit フック全体を実行
lefthook run pre-commit

# 特定のコマンドのみスキップ
LEFTHOOK_EXCLUDE=ktfmt-check git commit -m "メッセージ"
```

## シークレット管理（fnox + 1Password）

ローカル開発で必要なシークレット（当面は GitHub MCP 用の `GITHUB_PERSONAL_ACCESS_TOKEN`）は、平文で shell profile に `export` せず、**1Password に保管＋必要時だけ env へ展開**する。仕組みは mise 管理の [fnox](https://fnox.jdx.dev/) で統一する。

- `fnox.toml` には `op://` 参照のみを書き（秘密情報を含まない）、**git にコミットしてよい**
- 値の解決は 1Password CLI (`op`) 経由。`op` がサインイン済み（デスクトップアプリ連携）なら**サービスアカウントトークンは不要**
- MCP への供給は `fnox exec -- claude` で起動し、`.mcp.json` の `${GITHUB_PERSONAL_ACCESS_TOKEN}` を env 補間する

運用手順・前提セットアップの詳細は **`.claude/rules/secrets.md`** を参照。

## OpenAPI/Swagger

このプロジェクトは `springdoc-openapi` を使用して API ドキュメントを自動生成します：

- **エンドポイント**: `/swagger-ui.html`（アプリケーション起動後）
- **OpenAPI JSON**: `/v3/api-docs`

コントローラーのメソッドには `@Operation`, `@ApiResponse`, `@Content` などのアノテーションを付与してドキュメント化します。`@OpenAPIDefinition`（タイトル・タグ等の全体定義）は `ApiApplication.kt` に付与しています。

## Spring Boot Actuator

ヘルスチェックエンドポイントが `/actuator/health` で公開されています：

- **公開設定**: `application.yml` で設定
- **詳細表示**: 認可時のみ表示
- **テスト**: `HealthEndpointTest.kt` で動作確認

## インフラストラクチャ（Terraform）

`infra/` ディレクトリに Terraform 構成を管理しています。HCP Terraform（旧 Terraform Cloud）をバックエンドとして使用。

### ディレクトリ構成

```
infra/
├── main.tf              # 共有リソースとモジュール呼び出し
├── providers.tf         # Google プロバイダー設定
├── terraform.tf         # Terraform / バックエンド設定
├── variables.tf         # ルート変数（wif_project_number など）
└── modules/
    ├── cicd/            # CI/CD パイプライン基盤
    │   ├── main.tf      # deployer SA、WIF バインディング、AR 書き込み権限
    │   ├── variables.tf
    │   └── outputs.tf
    └── cloudrun/        # Cloud Run デプロイ基盤
        ├── main.tf      # api-runner SA、run.developer 権限、actAs 権限
        ├── variables.tf
        └── outputs.tf
```

### モジュール

- **cicd**: GitHub Actions がイメージをビルド・プッシュするために必要なリソース群（deployer SA、WIF、AR 権限）
- **cloudrun**: Cloud Run へのデプロイと実行に必要なリソース群（api-runner SA、デプロイ権限）

### コマンド

```bash
# 初期化
terraform init

# 差分確認
terraform plan

# 適用
terraform apply

# フォーマット
terraform fmt -recursive
```

## 注意事項

- このプロジェクトは現在、永続化層（データベース、リポジトリ）を持ちません
- ドメインモデルは探索的な実装であり、TODO コメントが含まれています
- コード品質を重視しており、CI で ktfmt / detekt / EditorConfig のチェックが自動実行されます
