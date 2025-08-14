# ヘルスチェックエンドポイント

## 概要
このアプリケーションはSpring Boot Actuatorを使用してヘルスチェックエンドポイントを提供しています。

## エンドポイント
- **URL**: `/actuator/health`
- **メソッド**: GET
- **レスポンス例**:
  ```json
  {
    "status": "UP"
  }
  ```

## テスト方法
1. アプリケーションを起動:
   ```bash
   ./gradlew bootRun
   ```

2. ヘルスチェックエンドポイントにアクセス:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. 期待されるレスポンス:
   - ステータスコード: 200
   - レスポンス: `{"status":"UP"}`

## セキュリティ設定
- ヘルスエンドポイントのみが公開されています
- 詳細情報は認証された場合のみ表示されます
- 他のactuatorエンドポイント（info, metrics等）は公開されていません
