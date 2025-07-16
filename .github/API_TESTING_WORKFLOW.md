# API Testing Workflow

このGitHub Actionsワークフローは、Pull Request作成時にAPIプロジェクトのテストを自動実行します。

## 動作仕様

### トリガー条件
- Pull Requestが`main`ブランチに対して作成された時
- Pull Requestが更新された時（新しいコミットがプッシュされた時）
- Pull Requestが再度開かれた時

### 実行内容
1. リポジトリのコードをチェックアウト
2. Java 21環境をセットアップ
3. Gradleの依存関係をキャッシュ（ビルド時間短縮のため）
4. Gradleラッパーに実行権限を付与
5. APIプロジェクトのテストを実行（`./gradlew test`）

### チェック結果
- **テストが成功**: Pull Requestのチェックが成功し、マージが可能
- **テストが失敗**: Pull Requestのチェックが失敗し、マージが不可能

## 技術詳細

- **実行環境**: Ubuntu latest
- **Javaバージョン**: Java 21 (Temurin distribution)
- **テストコマンド**: `./gradlew test --no-daemon --stacktrace`
- **作業ディレクトリ**: `./api`

## ファイル
- ワークフロー定義: `.github/workflows/api-tests.yml`