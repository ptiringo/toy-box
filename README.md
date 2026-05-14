# toy-box

## 開発環境

### コードスタイルとフォーマット

このプロジェクトでは、Kotlin コードのフォーマット統一のために **ktfmt** を、静的解析のために **detekt** を使用しています。
ktfmt は Kotlin 公式コーディング規約（`kotlinlang-style`、4 space indent / 100 char limit）に準拠した
フォーマッタで、設定項目を持たず常に同じ結果を出力します。detekt は命名規則・コードスメル・複雑度などを検出する Linter で、`config/detekt/detekt.yml` のプロジェクト固有設定でルールを調整しています。

#### Gradle タスク

| タスク | 用途 |
|---|---|
| `./gradlew ktfmtCheck` | フォーマット違反をチェックします |
| `./gradlew ktfmtFormat` | コードを自動フォーマットします |
| `./gradlew detekt` | 静的解析を実行します（レポートは `build/reports/detekt/`） |
| `./gradlew detektGenerateConfig` | detekt 設定の雛形を再生成します |

#### CI での自動チェック

Pull Request を作成すると、GitHub Actions で自動的に ktfmt チェックと detekt 解析が実行されます。
違反がある場合、CI は失敗します。

#### pre-commit フック

このプロジェクトでは **Lefthook** を使用して Git フックを管理しています。

Lefthook をセットアップするには：

```bash
lefthook install
```

これにより、以下のフックが自動的に有効化されます：

- **pre-commit**: コミット前に ktfmt チェック、detekt、EditorConfig チェックを実行
- **pre-push**: プッシュ前に全テストを実行
- **commit-msg**: コミットメッセージの形式をチェック

特定のフックをスキップしたい場合：

```bash
LEFTHOOK_EXCLUDE=ktfmt-check git commit -m "メッセージ"
```
