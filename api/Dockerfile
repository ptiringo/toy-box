# Spring Boot WebFlux API Dockerfile
# ベストプラクティス準拠：軽量イメージ、セキュリティ考慮
# 注意: このDockerfileは事前にビルドされたJARファイルを使用します
# ビルド方法: ./gradlew bootJar を実行してからdocker buildを実行してください

# ランタイムステージ - 軽量なJREイメージを使用
FROM eclipse-temurin:21-jre-alpine

# セキュリティ：非rootユーザーでアプリケーションを実行
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

# 作業ディレクトリの設定
WORKDIR /app

# 事前にビルドされたJARファイルをコピー
COPY build/libs/api-*-SNAPSHOT.jar app.jar

# ファイルの所有者を変更
RUN chown appuser:appgroup app.jar

# 非rootユーザーに切り替え
USER appuser

# アプリケーションポートの公開
EXPOSE 8080

# JVMメモリ設定とアプリケーション起動
# コンテナ環境に最適化されたJVM設定
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# ヘルスチェックの設定（/api/hello エンドポイントを使用）
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/hello || exit 1

# アプリケーションの起動
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]