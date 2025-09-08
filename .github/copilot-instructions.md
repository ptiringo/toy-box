# GitHub Copilot グローバル指示 (Global Instructions)

## 基本方針（Basic Guidelines）

### 言語とスタイル（Language and Style）
- **コメントとドキュメントは日本語で記述してください**
- 変数名、関数名、クラス名は英語で記述し、意味が明確になるようにしてください
- コードの説明やドキュメンテーションコメントは日本語で詳細に記述してください
- コミットメッセージも日本語で記述してください

## ツール管理（Tool Management）

### mise によるツール管理
このリポジトリでは **mise** を使用してプロジェクトで必要なツールを管理しています：

- **設定ファイル**: `mise.toml` にプロジェクトで使用するツールとバージョンを定義

### 開発者向け mise 使用方法
- **ツールのインストール**: `mise install` コマンドで定義されたツールを一括インストール
- **ツールの確認**: `mise list` コマンドで現在インストールされているツールを確認
- **自動有効化**: プロジェクトディレクトリに入ると自動的に適切なバージョンのツールが有効化されます

### CI/CD での mise 使用
GitHub Actionsワークフローでも mise を使用してツール管理の一貫性を保っています。
新しいツールを追加する際は `mise.toml` ファイルを更新してください。

## 命名規則とコード構成（Naming Conventions and Code Organization）

### 命名規則
- **クラス名**: PascalCase（例: UserService, OrderHandler）
- **関数名**: camelCase（例: createUser, validateInput）
- **定数**: UPPER_SNAKE_CASE（例: MAX_RETRY_COUNT）
- **プロパティ**: camelCase（例: userId, emailAddress）

## EditorConfig 準拠（EditorConfig Compliance）

### ファイル形式の遵守
- **全てのファイルはEditorConfigの設定に従う必要があります**
- `.editorconfig` ファイルで定義された以下の設定を厳密に守ってください：
  - `end_of_line = lf`: 改行コードはLF（Unix形式）を使用
  - `insert_final_newline = true`: ファイル末尾に必ず改行を挿入
  - `trim_trailing_whitespace = true`: 行末の空白文字を削除
  - `charset = utf-8`: UTF-8エンコーディングを使用
- **マークダウンファイルの特例**: `*.md` ファイルでは行末空白の削除は無効（`trim_trailing_whitespace = false`）

### CI/CDでの自動チェック
- EditorConfig Checkがプルリクエスト時に自動実行されます
- 違反があると自動的にエラーとなるため、コミット前に必ず確認してください
- ローカルでの事前チェックを推奨します

## Pre-commit Hooks with Lefthook

このプロジェクトでは Lefthook を使用した pre-commit hooks を導入しています。

### セットアップ

プロジェクトをクローンした後、以下のコマンドで git hooks をインストールしてください：

```bash
lefthook install
```

### 実行される検証項目

#### pre-commit

コミット前に以下の検証が自動実行されます：

1. **EditorConfig チェック** - `.editorconfig` の設定に準拠しているかチェック
2. **API テスト** - 変更されたKotlinファイルに関連するテストの実行

#### pre-push

プッシュ前に全体のテストスイートが実行されます。

#### commit-msg

コミットメッセージが conventional commit 形式に準拠しているかチェックします。

### 手動実行

フックを手動で実行したい場合：

```bash
# 全ての pre-commit hooks を実行
lefthook run pre-commit

# 特定のコマンドのみ実行
lefthook run pre-commit api-test
```

### フックのスキップ

緊急時にフックをスキップしたい場合：

```bash
# pre-commit hooks をスキップしてコミット
git commit --no-verify -m "緊急修正"

# 特定のコマンドをスキップ
LEFTHOOK_EXCLUDE=api-test git commit -m "テストをスキップしてコミット"
```

### トラブルシューティング

#### Lefthook がインストールされていない場合

```bash
# Ubuntu/Debian
curl -1sLf 'https://dl.cloudsmith.io/public/evilmartians/lefthook/setup.deb.sh' | sudo -E bash
sudo apt install lefthook

# または直接ダウンロード
wget "https://github.com/evilmartians/lefthook/releases/download/v1.10.0/lefthook_1.10.0_Linux_x86_64" -O lefthook
chmod +x lefthook && sudo mv lefthook /usr/local/bin/
```

#### hooks が実行されない場合

```bash
# hooks を再インストール
lefthook install

# hooks の状態を確認
lefthook version
git config --list | grep hook
```

# API開発専用指示 (API Development Instructions)

## プロジェクト構造（Project Structure）
このディレクトリはKotlin Spring Boot WebFluxを使用したAPIプロジェクトです：
- `api/` ディレクトリにメインのAPIコードが含まれています
- Gradle Kotlin DSLを使用してビルド設定を管理しています
- WebFluxとコルーチンを使用した非同期プログラミングを採用しています

## セキュリティベストプラクティス（Security Best Practices）

### コード提案時の考慮事項
- **入力値検証**: 全ての外部入力に対して適切な検証を実装してください
- **SQLインジェクション対策**: データベースクエリではパラメータ化クエリを使用してください
- **機密情報の保護**: API キー、パスワード、その他の機密情報をハードコーディングしないでください
- **ログ出力**: 機密情報をログに出力しないよう注意してください

## パフォーマンスと保守性（Performance and Maintainability）

### パフォーマンス指針
- **非同期処理**: WebFluxとコルーチンを活用した非ブロッキング処理を推奨します
- **効率的なデータ処理**: Streamやシーケンスを適切に使用してメモリ効率を考慮してください
- **キャッシュ戦略**: 適切な場所でキャッシュの実装を提案してください
- **データベース最適化**: N+1問題の回避やクエリ最適化を考慮してください

### コード品質と保守性
- **単一責任原則**: 各クラス・関数は単一の責任を持つよう設計してください
- **依存性注入**: Spring DIコンテナを適切に活用してください
- **イミュータブル設計**: 可能な限りデータクラスをimmutableに設計してください
- **関数型プログラミング**: Kotlinの関数型機能を活用してください

## テスト開発（Testing Development）

### Spring Boot テスト
- **@SpringBootTest**: 統合テストでアプリケーションコンテキスト全体をテストする場合に使用
- **@WebFluxTest**: WebFluxコントローラーの単体テストに使用
- **@DataR2dbcTest**: R2DBCリポジトリのテストに使用
- **TestContainers**: データベースなど外部リソースが必要な場合は積極的に活用

### テストアノテーション（Test Annotations）
- **JUnit5アノテーションの使用**: テストメソッドには `org.junit.jupiter.api.Test` アノテーションを使用してください
- **kotlin.test.Testは使用禁止**: マルチプラットフォーム対応が不要なため、JUnit5の機能を積極的に活用します
- **JUnit5拡張機能の活用**: `@Nested`、`@ParameterizedTest`、`@BeforeEach`、`@AfterEach` などの機能を積極的に使用してください

### アサーション方法（Assertion Methods）
- **Kotlin assert関数の優先利用**: 単体テストでは kotlin.test パッケージのアサーション関数ではなく、Kotlin 標準の `assert` 関数（Power Assert）を優先的に使用してください
- **Power Assertの利点**: 詳細なデバッグ情報が自動的に生成され、テスト失敗時の原因特定が容易になります
- **適切な使い分け**: 
  - 単体テスト: `assert` 関数を使用
  - 統合テスト（WebFlux）: `WebTestClient` のアサーションメソッドを使用
  - 特定のフレームワークテスト: そのフレームワークに適したアサーション方法を使用

## ドキュメンテーション（Documentation）

### ドキュメンテーション標準（Documentation Standards）

#### コメント記述
- **KDoc**: パブリックなAPI要素にはKDocコメントを必ず記述してください
- **複雑なロジック**: 複雑なビジネスロジックには日本語で詳細な説明を追加してください
- **TODO/FIXME**: 将来の改善点や既知の問題を明記してください

#### API ドキュメント
- **OpenAPI/Swagger**: REST API の仕様書は自動生成を活用してください
- **エンドポイント説明**: 各エンドポイントの目的、パラメータ、レスポンス例を明記してください
- **エラーハンドリング**: 想定されるエラーケースとその対処法を文書化してください


# テスト開発専用指示 (Testing Development Instructions)

## テスト指針（Testing Guidelines）

### ユニットテスト
- **テストケース命名**: 日本語でテストの意図を明確に表現してください
- **テストデータ**: 意味のあるテストデータを使用してください
- **モック使用**: 外部依存をモックして単体テストの独立性を保ってください
- **例外テスト**: 正常系だけでなく異常系のテストも実装してください

### テストコード品質
- **可読性**: テストコードは仕様書の役割も果たすため、意図が明確に伝わるように記述してください
- **保守性**: テスト対象コードの変更に対して適切に追従できるよう、疎結合なテスト設計を心がけてください
- **独立性**: テスト間の依存関係を排除し、単独で実行可能なテストを作成してください
