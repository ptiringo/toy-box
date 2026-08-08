---
name: run-frontend
description: Use when starting or running the toy-box frontend (frontend/ の Vite+React SPA) locally, or driving it in a browser to verify the login/馬一覧 UI. Covers npm run dev, the mandatory .env.local Firebase config (missing it = blank white screen), headless-browser driving, and the full E2E prerequisites (local Postgres + GCP_PROJECT_ID bootRun + 実 Identity Platform テナント). 「フロントを起動」「npm run dev」「画面が真っ白」「ログイン画面が出ない」等が合図。
---

# フロント（Vite+React SPA）をローカル起動・駆動する

`frontend/` の軽量 SPA（#612。Firebase Auth でログイン → `GET /api/bloodHorses` で馬一覧）を起動して動作確認するレシピ。人間向けの詳細は `frontend/README.md` が出所。ここは「起動して駆動する」ときの要点。

> **既知の破壊（`#705`）**: バックエンドは `/api/bloodHorses` を含む全ドメイン API を
> `/api/worlds/{worldId}/...` 配下へ移した（ADR-0067）。フロントはまだ旧パスを叩いており一覧取得は必ず失敗する
> （401 → ログイン後は 200 ではなく 404/経路不一致になる）。追随は `#714`。起動・駆動自体はできるので、
> このスキルの手順はそのまま使ってよい。

## 起動（UI を出すだけ）

```bash
cd frontend
npm install                  # 初回のみ
cp .env.example .env.local   # 実 Identity Platform テナントの Firebase Web config で値を埋める
npm run dev                  # http://localhost:5173（/api/* を :8080 へ proxy）
```

Node は `mise.toml` の `node`（`mise install` で入る）。

## 最重要の罠: `.env.local` 無し ＝ 白画面

`.env.local`（`VITE_FIREBASE_API_KEY` / `_AUTH_DOMAIN` / `_PROJECT_ID`）が無いと **root div が空のまま＝画面が真っ白**になる。firebase の初期化が config 無しで失敗し、React アプリのマウントごと落ちるため。**dev server のログは正常（`ready in ...`）なのに画面が空**、が指紋。「ビルド失敗」と誤診せず、まず `.env.local` を疑う。config があれば「認証状態を確認中…」→ 未ログインなら `/login` のフォームが出る。

## ブラウザで駆動して確認する

`chromium-cli` / `playwright` は未インストール。macOS なら OS の Chrome を headless で叩き、JS 実行後の DOM を取って root の中身を見る:

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless=new --disable-gpu --no-sandbox --dump-dom \
  --virtual-time-budget=8000 http://localhost:5173/ | grep -oE '<div id="root">.*'
```

- root が空（`<div id="root"></div>`）→ `.env.local` 欠如か JS エラー（白画面）。
- root に「認証状態を確認中…」や「ログイン」→ React はマウント済み（起動成功）。

dev server は `npm run dev` をバックグラウンド起動し、`curl --retry ... http://localhost:5173/` で ready を待ってから叩く。

## 完全 E2E（実ログイン → 一覧）

フロント単体では一覧データは出ない。追加で3つ要る:

1. **ローカル Postgres**: バックの datasource は実行環境が外部供給する（ADR-0044）。DB を1つ用意し `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` を渡す（Flyway が起動時に schema を作る）。
2. **バック起動**（リポジトリルートで）: `GCP_PROJECT_ID=<projectId> ./gradlew bootRun`（:8080、実 issuer で JWT 検証）。
3. **実 Identity Platform テナント**: email/password サインインを有効化しテストユーザーを作成、その Web config を `.env.local` に。**`projectId` はバックの `GCP_PROJECT_ID` と一致させる**（issuer を揃えないとトークンが 401 になる）。

`localhost:5173` → ログイン → 一覧、で右上のステータスが未ログイン `401` → ログイン後 `200` に変わる（これが PR の手動 E2E）。

## Common Mistakes

| 症状 | 原因・対処 |
|---|---|
| 画面が真っ白・root が空 | `.env.local` 欠如（firebase 初期化失敗）。dev server ログは正常なのが指紋。 |
| 「認証状態を確認中…」で固まる | ダミー/実在しない `projectId`。onAuthStateChanged が解決しない。実テナントが要る。 |
| 一覧が空 / 401 のまま | バック未起動・DB 未接続・トークン不正（フロント `projectId` とバック `GCP_PROJECT_ID` の不一致）。 |
| `npm run dev` が node 無しで落ちる | `mise install` で `node` を入れる。 |
