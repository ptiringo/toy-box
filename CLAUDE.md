# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Kotlin Spring Boot WebFlux を使用した API プロジェクトです。複数のドメインモデル（競馬、エンターテイメント、テニス）を探索する sandbox プロジェクトとして開発されています。

## 開発コマンド

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
# ktlint チェック
./gradlew ktlintCheck

# ktlint 自動フォーマット
./gradlew ktlintFormat

# 全チェック実行
./gradlew check
```

### 単一テストの実行

```bash
# 特定のテストクラスを実行
./gradlew test --tests "HelloControllerTest"

# 特定のテストメソッドを実行
./gradlew test --tests "HelloControllerTest.helloエンドポイントがHello Worldを返すこと"
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
data class Command<T>(
    val data: T,
    val timestamp: Instant = Instant.now()
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

## コーディング規約

### 言語とスタイル

- **コメントとドキュメント**: 日本語で記述
- **変数名、関数名、クラス名**: 英語で記述（意味を明確に）
- **コミットメッセージ**: 日本語で記述、Conventional Commits 形式に準拠

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

現在管理されているツール：
- `editorconfig-checker`: EditorConfig 準拠チェック
- `lefthook`: Git フック管理

### Lefthook

Git フックは **Lefthook** で管理されています（`lefthook.yml` 参照）。

#### セットアップ

```bash
lefthook install
```

#### 実行されるフック

- **pre-commit**: EditorConfig チェック、ktlint チェック
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

## 注意事項

- このプロジェクトは現在、永続化層（データベース、リポジトリ）を持ちません
- ドメインモデルは探索的な実装であり、TODO コメントが含まれています
- コード品質を重視しており、CI で ktlint と EditorConfig のチェックが自動実行されます
