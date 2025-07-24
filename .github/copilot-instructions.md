# GitHub Copilot グローバル指示 (Global Instructions)

## 基本方針（Basic Guidelines）

### 言語とスタイル（Language and Style）
- **コメントとドキュメントは日本語で記述してください**
- 変数名、関数名、クラス名は英語で記述し、意味が明確になるようにしてください
- コードの説明やドキュメンテーションコメントは日本語で詳細に記述してください
- コミットメッセージも日本語で記述してください

### プロジェクト構造（Project Overview）
このリポジトリは複数のコンポーネントから構成されており、各ディレクトリには専用の指示ファイルが配置されています：
- `api/` ディレクトリ: Kotlin Spring Boot WebFlux API (詳細は `instructions/api.instructions.md` を参照)
- テスト関連: 詳細は `instructions/testing.instructions.md` を参照
- ドキュメント関連: 詳細は `instructions/docs.instructions.md` を参照

### ディレクトリ別指示ファイル
- **API開発**: `.github/instructions/api.instructions.md`
- **テスト開発**: `.github/instructions/testing.instructions.md`
- **ドキュメント作成**: `.github/instructions/docs.instructions.md`

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

これらの指針に従って、高品質で保守性が高く、セキュリティを考慮した開発を支援してください。各専門領域については対応する指示ファイルを参照してください。
