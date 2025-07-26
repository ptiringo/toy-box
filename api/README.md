# toy-box API

## 概要
Spring Boot WebFlux を使用したRESTful API プロジェクトです。

## 開発環境での実行

### 通常の実行
```shell
./gradlew bootRun
```

### Dockerを使用した実行

#### 1. JARファイルのビルド
```shell
./gradlew bootJar
```

#### 2. Dockerイメージのビルド
```shell
docker build -t toybox-api .
```

#### 3. コンテナの起動
```shell
docker run -p 8080:8080 toybox-api
```

#### 4. APIエンドポイントの確認
```shell
curl http://localhost:8080/api/hello
```

## Dockerイメージの特徴

- **ベースイメージ**: Eclipse Temurin 21 JRE Alpine（軽量）
- **セキュリティ**: 非rootユーザーでアプリケーションを実行
- **JVM最適化**: コンテナ環境に最適化されたメモリ設定
- **ヘルスチェック**: `/api/hello` エンドポイントを使用した自動ヘルスチェック
- **イメージサイズ**: 約267MB（軽量）
- **起動時間**: 約2.6秒（高速）

## エンドポイント

- `GET /api/hello` - Hello Worldメッセージを返す
