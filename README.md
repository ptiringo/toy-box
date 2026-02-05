# toy-box

## 開発環境

### コードスタイルとフォーマット

このプロジェクトでは、Kotlin コードの品質とスタイルの統一のために **ktlint** を使用しています。

#### ktlint の使い方

以下のGradleタスクが利用可能です：

- **`./gradlew ktlintCheck`** - コードスタイルの違反をチェックします
- **`./gradlew ktlintFormat`** - コードを自動フォーマットします

#### CI での自動チェック

Pull Request を作成すると、GitHub Actions で自動的に ktlint チェックが実行されます。
スタイル違反がある場合、CI は失敗します。

#### ベースラインについて

`config/ktlint/baseline.xml` には、既存のコードベースで発見されたスタイル違反の「ベースライン」が記録されています。
新しいコードでは、これらの違反を繰り返さないようにしてください。

#### pre-commit フック（オプション）

コミット前に自動的にフォーマットを適用したい場合は、以下のGit hookを設定できます：

```bash
./gradlew addKtlintFormatGitPreCommitHook
```

チェックのみを実行したい場合：

```bash
./gradlew addKtlintCheckGitPreCommitHook
```
