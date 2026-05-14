# toy-box

## 開発環境

### コードスタイルとフォーマット

このプロジェクトでは、Kotlin コードのフォーマット統一のために **ktfmt** を使用しています。
ktfmt は Kotlin 公式コーディング規約（`kotlinlang-style`、4 space indent / 100 char limit）に準拠した
フォーマッタで、設定項目を持たず常に同じ結果を出力します。

#### ktfmt の使い方

以下の Gradle タスクが利用可能です：

- **`./gradlew ktfmtCheck`** - フォーマット違反をチェックします
- **`./gradlew ktfmtFormat`** - コードを自動フォーマットします

#### CI での自動チェック

Pull Request を作成すると、GitHub Actions で自動的に ktfmt チェックが実行されます。
フォーマット違反がある場合、CI は失敗します。

#### pre-commit フック

このプロジェクトでは **Lefthook** を使用して Git フックを管理しています。

Lefthook をセットアップするには：

```bash
lefthook install
```

これにより、以下のフックが自動的に有効化されます：

- **pre-commit**: コミット前に ktfmt チェックと EditorConfig チェックを実行
- **pre-push**: プッシュ前に全テストを実行
- **commit-msg**: コミットメッセージの形式をチェック

特定のフックをスキップしたい場合：

```bash
LEFTHOOK_EXCLUDE=ktfmt-check git commit -m "メッセージ"
```
