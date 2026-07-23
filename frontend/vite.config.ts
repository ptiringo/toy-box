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
  },
});
