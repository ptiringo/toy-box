import { defineConfig, devices } from "@playwright/test";

const BASE_URL = "http://127.0.0.1:5173";

/**
 * ブラウザ E2E（#725）。ログイン → 世界作成 → 馬一覧までを実ブラウザで通す。
 *
 * 3 プロセスを webServer が起動する。PostgreSQL も webServer（bootTestRun のコマンド）の
 * docker compose up -d --wait でこの config が立てる。bootTestRun では
 * spring-boot-docker-compose の自動配線が効かないため（理由は当該コメント参照）。
 * 立てた DB は Playwright の終了時に落ちないので、CI では明示的な teardown が要る。
 */
export default defineConfig({
  testDir: "./e2e",
  // 通しシナリオは共有の DB とバックエンドを踏むため直列で回す。
  workers: 1,
  fullyParallel: false,
  // CI では取りこぼしを見逃さないよう .only を失敗にする。
  forbidOnly: !!process.env.CI,
  // ローカルは 0（本物の不安定さを隠さない）。CI は共有ランナーの CPU 枯渇で落ちることがあるため 1 回だけ
  // 許す（Task 5 で実測した flake は expect のポーリングが 15 秒設定に対し 27.85 秒かかる形で現れた）。
  retries: process.env.CI ? 1 : 0,
  // 既定の 5 秒だと、サインイン → :provision → JVM の初回リクエスト → 描画 を待つアサーションが
  // CI ランナー（ローカルより数倍遅い）で溢れうる。
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [["html", { open: "never" }], ["list"]] : [["list"]],
  use: {
    baseURL: BASE_URL,
    // 失敗した実行だけ trace を残す（CI の artifact で原因を追うため）。
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: "npm run emulator",
      url: "http://127.0.0.1:9099",
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      stdout: "pipe",
      stderr: "pipe",
    },
    {
      // リポジトリルートで動かす（compose.yaml と gradlew がそこにあるため）。
      //
      // DB を先に立てて datasource を env で渡すのは、spring-boot-docker-compose が
      // developmentOnly 依存（build.gradle.kts:49 / #451）で **test runtime classpath に載らない**ため。
      // bootRun と違い bootTestRun では compose.yaml の自動配線が効かないので、本番 Cloud Run と
      // 同じ SPRING_DATASOURCE_* の注入経路で明示的に供給する（Task 3 で実証済み）。
      command: "docker compose up -d --wait && ./gradlew bootTestRun",
      cwd: "..",
      url: "http://127.0.0.1:8080/actuator/health",
      reuseExistingServer: !process.env.CI,
      // PostgreSQL の pull・Flyway 適用・Spring 起動を含むため長めに取る。
      timeout: 300_000,
      env: {
        GCP_PROJECT_ID: "toy-box-e2e",
        SPRING_DATASOURCE_URL: "jdbc:postgresql://127.0.0.1:5432/toybox",
        SPRING_DATASOURCE_USERNAME: "toybox",
        SPRING_DATASOURCE_PASSWORD: "toybox",
      },
      stdout: "pipe",
      stderr: "pipe",
    },
    {
      // dev server ではなく本番ビルド成果物を配信する（退行を検出したいのはビルド後の姿）。
      //
      // --host 127.0.0.1 は必須。既定の vite preview は IPv6 の [::1] だけに bind し、
      // IPv4 の 127.0.0.1 へ到達できない（Task 4 で実測）。BASE_URL を 127.0.0.1 で書いている以上、
      // bind 先を明示しないと webServer の readiness 判定が永久に失敗する。
      command:
        "npm run build -- --mode e2e && npm run preview -- --port 5173 --strictPort --host 127.0.0.1",
      url: BASE_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      stdout: "pipe",
      stderr: "pipe",
    },
  ],
});
