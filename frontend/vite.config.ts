import react from "@vitejs/plugin-react";
// `test` セクションを型として認めるのは vitest/config の defineConfig（vite のそれには無い）。
import { defineConfig } from "vitest/config";

// dev server（:5173）から /api/* を bootRun（:8080）へ転送し、同一オリジン扱いにする（CORS 不要）。
const apiProxy = {
  "/api": {
    target: "http://localhost:8080",
    changeOrigin: true,
  },
};

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: apiProxy,
  },
  // ブラウザ E2E（#725）は dev server ではなく本番ビルド成果物を配信して検証するため、
  // preview にも dev と同じ proxy を置く。
  //
  // `host` の明示は必須。vite preview は既定で IPv6 の [::1] だけに bind するため、
  // `http://127.0.0.1:5173` で readiness を待つ Playwright の webServer から到達できない（#725 で実測）。
  // bind 先の出所をこの 1 箇所に集約するため、起動コマンド側では `--host` を渡さない。
  preview: {
    host: "127.0.0.1",
    port: 5173,
    proxy: apiProxy,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    // e2e/ は Playwright が回す。vitest の既定 include は *.spec.ts も拾うため明示的に外す
    // （既定の exclude を上書きするので node_modules / dist も併せて書く）。
    exclude: ["**/node_modules/**", "**/dist/**", "e2e/**"],
  },
});
