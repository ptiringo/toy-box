# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

### ドメイン駆動設計

#### Value Object パターン

型安全性のために `@JvmInline value class` を使用：

```kotlin
@JvmInline
value class JockeyId(val value: UUID)

@JvmInline
value class BloodHorseId(val value: UUID)
```

ゼロコスト抽象化により、異なるエンティティの ID を誤って混同することを防ぎます。

#### Entity パターン

エンティティは UUID ベースの同一性を持ちます：

- **ID による等価性**: `equals()` と `hashCode()` は ID のみで実装
- **UUID 生成戦略**:
  - `UUID.randomUUID()`: シンプルなランダム生成
  - `Generators.timeBasedEpochRandomGenerator()`: タイムベース生成（`java-uuid-generator` ライブラリ使用）

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

```
com.example.api/
├── ApiApplication.kt    # エントリーポイント（@OpenAPIDefinition もここ）
├── controller/          # @RestController（HTTP エンドポイント）
└── domain/              # ドメインロジック（Spring に依存しない）
    ├── horseracing/     # 競馬ドメイン
    ├── sakamichi/       # エンターテイメントドメイン
    └── tennis/          # スポーツドメイン
```

**設計原則**: ドメインパッケージはフレームワーク非依存。コントローラーは HTTP とドメインロジックの薄いアダプター層として機能。

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
- `editorconfig-checker`: EditorConfig 準拠チェック
- `java` (Temurin 21): Gradle / Kotlin のビルドおよびテスト実行用 JDK
- `lefthook`: Git フック管理
- `terraform`: インフラ構成管理

**Java バージョン管理について**: JDK のバージョン要件は `build.gradle.kts` の Gradle toolchain で宣言しています（`languageVersion = 21`）。実体の JDK は mise が提供し、Gradle の toolchain auto-detection が `JAVA_HOME` / `PATH` 経由で検出します。

### Lefthook

Git フックは **Lefthook** で管理されています（`lefthook.yml` 参照）。

#### セットアップ

```bash
lefthook install
```

#### 実行されるフック

- **pre-commit**（並列実行）: EditorConfig チェック、ktfmt チェック、detekt 静的解析、Terraform fmt チェック、Terraform validate
- **pre-push**: 全テスト実行
- **commit-msg**: Conventional Commits 形式のチェック

#### フックの手動実行

```bash
# pre-commit フック全体を実行
lefthook run pre-commit

# 特定のコマンドのみスキップ
LEFTHOOK_EXCLUDE=ktfmt-check git commit -m "メッセージ"
```

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
