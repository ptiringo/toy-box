import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// dev server（:5173）から /api/* を bootRun（:8080）へ転送し、同一オリジン扱いにする（CORS 不要）。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    // モックの呼び出し履歴はテストをまたいで積み上がる（既定は false）。放置すると
    // toHaveBeenCalledWith が前のテストの履歴で通り、偽陰性になる。
    // 実装（mockResolvedValue 等）は各テストが入れ直す前提なので、履歴だけ落とす。
    clearMocks: true,
    // vi.spyOn で差し替えた実装を各テストの前に戻す（afterEach(vi.restoreAllMocks) の代替）。
    restoreMocks: true,
    // vi.stubGlobal で差し替えた global（fetch 等）を各テストの前に戻す。restoreMocks では
    // 戻らない（vi.restoreAllMocks は spy のみが対象）ため、これが無いと stub が漏れ続ける。
    unstubGlobals: true,
  },
});
