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

これらの指針に従って、高品質で保守性が高く、セキュリティを考慮した開発を支援してください。各専門領域については対応する指示ファイルを参照してください。
