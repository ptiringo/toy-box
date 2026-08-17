import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

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
  // 既定では IPv6 の [::1] だけに bind するため、127.0.0.1 で待つ側（Playwright の webServer）とは
  // --host 127.0.0.1 を明示して揃える（#725）。
  preview: {
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
