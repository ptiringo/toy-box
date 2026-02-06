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

#### pre-commit フック

このプロジェクトでは **Lefthook** を使用して Git フックを管理しています。

Lefthook をセットアップするには：

```bash
lefthook install
```

これにより、以下のフックが自動的に有効化されます：

- **pre-commit**: コミット前に ktlint チェックと EditorConfig チェックを実行
- **pre-push**: プッシュ前に全テストを実行
- **commit-msg**: コミットメッセージの形式をチェック

特定のフックをスキップしたい場合：

```bash
LEFTHOOK_EXCLUDE=ktlint-check git commit -m "メッセージ"
```
