# toy-box

## 開発環境

### アプリケーションの起動

```bash
# デフォルトプロファイルで起動
./gradlew bootRun

# 開発プロファイルで起動（詳細なログ、追加のActuatorエンドポイント）
./gradlew bootRun --args='--spring.profiles.active=dev'

# 本番プロファイルで起動（最小限のログ、セキュアな設定）
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### APIエンドポイントの使い方

アプリケーション起動後、以下のエンドポイントにアクセスできます：

#### Hello World API
```bash
# Hello Worldメッセージを取得
curl http://localhost:8080/api/hello

# レスポンス例
{
  "message": "Hello World"
}
```

#### OpenAPI / Swagger UI
ブラウザで以下のURLにアクセス：
```
http://localhost:8080/swagger-ui.html
```

#### Actuator ヘルスチェック
```bash
# ヘルスチェック
curl http://localhost:8080/actuator/health

# レスポンス例
{
  "status": "UP"
}
```

開発プロファイル（`dev`）で起動した場合は、追加のエンドポイントも利用可能：
```bash
# アプリケーション情報
curl http://localhost:8080/actuator/info

# メトリクス
curl http://localhost:8080/actuator/metrics

# 環境変数
curl http://localhost:8080/actuator/env
```

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
