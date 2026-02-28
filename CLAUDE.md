# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Kotlin Spring Boot WebFlux を使用した API プロジェクトです。複数のドメインモデル（競馬、エンターテイメント、テニス）を探索する sandbox プロジェクトとして開発されています。

## 開発コマンド

### 重要: mise を使用したコマンド実行

このプロジェクトでは mise を使用してツールバージョンを管理しています。**Claude Code や非対話型シェルからコマンドを実行する場合は、`mise exec --` プレフィックスを使用してください**。これにより、mise で管理されている正しいバージョンの Java やその他のツールが使用されます。

例：
```bash
# mise exec を使用したコマンド実行
mise exec -- ./gradlew build

# または環境変数を設定
eval "$(mise activate bash)"  # bash の場合
./gradlew build
```

対話型シェルで mise がすでにアクティブな場合は、直接 `./gradlew` コマンドを実行できます。

### ビルドとテスト

```bash
# ビルド
mise exec -- ./gradlew build

# テスト実行
mise exec -- ./gradlew test

# アプリケーション起動
mise exec -- ./gradlew bootRun
```

### コード品質チェック

```bash
# ktlint チェック
mise exec -- ./gradlew ktlintCheck

# ktlint 自動フォーマット
mise exec -- ./gradlew ktlintFormat

# 全チェック実行
mise exec -- ./gradlew check
```

### 単一テストの実行

```bash
# 特定のテストクラスを実行
mise exec -- ./gradlew test --tests "HelloControllerTest"

# 特定のテストメソッドを実行（メソッド名に応じてパターンを調整してください）
mise exec -- ./gradlew test --tests "HelloControllerTest.*hello*"
```

## アーキテクチャ

### 構成パターン

このプロジェクトは **Functional Routing** パターンを採用しています：

- **RouterConfig** (`config/RouterConfig.kt`): `coRouter { }` DSL でルーティングを定義
- **Handler** (`handler/`): コントローラーの代わりに suspend 関数ベースのハンドラーを使用
- **Domain** (`domain/`): フレームワークに依存しないピュアなドメインモデル

例：
```kotlin
// RouterConfig.kt
coRouter {
    GET("/api/hello", helloHandler::hello)
}

// HelloHandler.kt
suspend fun hello(request: ServerRequest): ServerResponse {
    return ok().bodyValueAndAwait(HelloResponse("Hello, World!"))
}
```

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
├── ApiApplication.kt    # エントリーポイント
├── config/              # Spring 設定（RouterConfig）
├── handler/             # WebFlux ハンドラー（ビジネスロジック）
└── domain/              # ドメインロジック（Spring に依存しない）
    ├── horseracing/     # 競馬ドメイン
    ├── sakamichi/       # エンターテイメントドメイン
    └── tennis/          # スポーツドメイン
```

**設計原則**: ドメインパッケージはフレームワーク非依存。ハンドラーは HTTP とドメインロジックの薄いアダプター層として機能。

### ロギング

このプロジェクトでは構造化ロギングを採用しています：

- **ロガーの取得**: `LoggerFactory.getLogger(クラス名::class.java)`
- **ログレベル**:
  - `DEBUG`: 開発時の詳細なトレース情報
  - `INFO`: 通常の運用情報
  - `WARN`: 警告（処理は継続可能）
  - `ERROR`: エラー（処理に失敗）
- **プロファイル別設定**:
  - `dev`: テキスト形式、DEBUGレベル
  - `prod`: JSON形式（Logstash形式）、INFOレベル

例：
```kotlin
private val logger = LoggerFactory.getLogger(HelloHandler::class.java)

logger.debug("デバッグ情報")
logger.info("処理を開始しました")
logger.warn("警告: {}", message)
logger.error("エラーが発生しました", exception)
```

### エラーハンドリング

グローバル例外ハンドラー（`GlobalExceptionHandler`）により、統一的なエラーレスポンスを提供：

- **IllegalArgumentException** → 400 Bad Request
- **IllegalStateException** → 409 Conflict
- **NoSuchElementException** → 404 Not Found
- **その他の例外** → 500 Internal Server Error

エラーレスポンス形式：
```json
{
  "timestamp": "2026-02-28T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "不正な引数です"
}
```

## コーディング規約

### 言語とスタイル

- **コメントとドキュメント**: 日本語で記述
- **変数名、関数名、クラス名**: 英語で記述（意味を明確に）
- **コミットメッセージ**: 日本語で記述、Conventional Commits 形式に準拠。最初に Conventional Commits ヘッダー（例: `feat: 新機能を追加`）を記述し、その後ファイルごとの詳細な変更内容を記述

### 命名規則

- **クラス名**: PascalCase（例: `UserService`, `OrderHandler`）
- **関数名**: camelCase（例: `createUser`, `validateInput`）
- **定数**: UPPER_SNAKE_CASE（例: `MAX_RETRY_COUNT`）
- **プロパティ**: camelCase（例: `userId`, `emailAddress`）

### テスト規約

#### アノテーション

- **JUnit 5 を使用**: `org.junit.jupiter.api.Test` アノテーションを使用
- **kotlin.test.Test は使用禁止**: マルチプラットフォーム対応が不要なため

#### アサーション

- **Kotlin の `assert` 関数を優先**: 単体テストでは Power Assert を活用
- **WebFlux テスト**: `WebTestClient` のアサーションメソッドを使用
- **テストケース命名**: 日本語でテストの意図を明確に表現

例：
```kotlin
@Test
fun `helloエンドポイントがHello Worldを返すこと`() {
    val response = handler.hello(mockRequest).awaitSingle()
    assert(response.statusCode() == HttpStatus.OK)
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
- `lefthook`: Git フック管理
- `terraform`: インフラ構成管理

**注意**: Java（JDK 21）は mise ではなく Gradle toolchain で管理されています。

## 環境とプロファイル

このプロジェクトでは環境別の設定をSpring Profilesで管理しています：

### デフォルト（application.yml）
- ポート: 8080（環境変数 `PORT` で上書き可）
- Actuator: `/actuator/health` のみ公開

### 開発環境（dev）
```bash
mise exec -- ./gradlew bootRun --args='--spring.profiles.active=dev'
```

- ログレベル: DEBUG
- ログ形式: テキスト形式（可読性重視）
- Actuator: health, info, metrics, env を公開
- セキュリティ: 緩和（開発効率重視）

### 本番環境（prod）
```bash
mise exec -- ./gradlew bootRun --args='--spring.profiles.active=prod'
```

- ログレベル: INFO
- ログ形式: JSON形式（Logstash）
- Actuator: health のみ公開
- セキュリティ: 厳格（セキュリティヘッダー有効）

## セキュリティ

### セキュリティヘッダー

`SecurityConfig` により以下のセキュリティヘッダーを自動設定：

- **X-Frame-Options**: DENY（クリックジャッキング対策）
- **Content-Security-Policy**: default-src 'self'（XSS対策）
- **Referrer-Policy**: 参照元情報の制御
- **Permissions-Policy**: 位置情報、マイク、カメラへのアクセス制限

### 認証・認可

現在は全エンドポイントを `permitAll()` で公開していますが、本番環境では以下を検討：

- OAuth2 / JWT 認証の導入
- エンドポイントごとの権限設定
- CSRF 保護の有効化（必要に応じて）

### Lefthook

Git フックは **Lefthook** で管理されています（`lefthook.yml` 参照）。

#### セットアップ

```bash
lefthook install
```

#### 実行されるフック

- **pre-commit**（並列実行）: EditorConfig チェック、ktlint チェック、Terraform fmt チェック、Terraform validate
- **pre-push**: 全テスト実行
- **commit-msg**: Conventional Commits 形式のチェック

#### フックの手動実行

```bash
# pre-commit フック全体を実行
lefthook run pre-commit

# 特定のコマンドのみスキップ
LEFTHOOK_EXCLUDE=ktlint-check git commit -m "メッセージ"
```

## OpenAPI/Swagger

このプロジェクトは `springdoc-openapi` を使用して API ドキュメントを自動生成します：

- **エンドポイント**: `/swagger-ui.html`（アプリケーション起動後）
- **OpenAPI JSON**: `/v3/api-docs`

ハンドラーには `@Operation`, `@ApiResponse`, `@Content` などのアノテーションを付与してドキュメント化します。

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
mise exec -- terraform init

# 差分確認
mise exec -- terraform plan

# 適用
mise exec -- terraform apply

# フォーマット
mise exec -- terraform fmt -recursive
```

## 注意事項

- このプロジェクトは現在、永続化層（データベース、リポジトリ）を持ちません
- ドメインモデルは探索的な実装であり、TODO コメントが含まれています
- コード品質を重視しており、CI で ktlint と EditorConfig のチェックが自動実行されます
