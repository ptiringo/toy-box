# toy-box フロントエンド（軽種馬シミュレータ）

ログインしたユーザーが実 Identity Platform にログインし、自分の世界（セーブデータ）を選んで保護 API
`GET /api/worlds/{worldId}/bloodHorses` を叩き、軽種馬一覧を見る軽量 SPA（Vite + React + TypeScript）。
認証・テナント分離（ADR-0067）を「目で見て触れる」ことが主眼の MVP（#612 / #714）。

## 前提

- **Node**: `mise.toml` の `node`（22 系）。リポジトリルートで `mise install` すれば入る。
- **実 Identity Platform テナント**: 手で触って確認するときのログインは Firebase Auth JS SDK 経由で実テナントに対して行う。email/password サインインを有効化し、テストユーザーを1人作っておく。
  自動のブラウザ E2E（`npm run test:e2e`）だけは Firebase Auth Emulator を使うので実テナントも `.env.local` も要らない（後述）。

## セットアップ

```bash
cd frontend
npm install
cp .env.example .env.local   # 実テナントの Firebase Web config で値を埋める（下記）
```

`.env.local`（gitignore 対象・コミットしない）に実値を入れる:

```dotenv
VITE_FIREBASE_API_KEY=<実テナントの apiKey>
VITE_FIREBASE_AUTH_DOMAIN=<project>.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=<project>
```

> **`projectId` はバックエンドの `GCP_PROJECT_ID` と一致させること**（両者の issuer を揃えるため。ずれると発行したトークンがバックの JWT 検証を通らず 401 になる）。
>
> Firebase Web config の `apiKey` は公開前提のクライアント識別子で、秘密ではない（ブラウザに埋まる）。ただし環境依存なので `.env.local` に置き、リポジトリにはコミットしない。

## 起動

```bash
npm run dev     # http://localhost:5173
```

Vite dev server が `/api/*` を `http://localhost:8080`（バックエンド）へ proxy するため、CORS 設定は不要（同一オリジン扱い）。

> **`.env.local` が無いと画面が真っ白（root が空）になる。** firebase の初期化が config 無しで失敗し、React アプリのマウントごと落ちるため。config を入れると「認証状態を確認中…」→ 未ログインなら `/login` のログインフォームが表示される。

## 完全に動かす（ログイン → 一覧）

フロント単体では一覧データは出ない。バックエンドと DB も要る:

1. **ローカル DB（PostgreSQL）**: バックの datasource は実行環境が外部供給する（[ADR-0044](../docs/adr/0044-adopt-prisma-postgres-for-production-db.md)）。ローカルに Postgres を1つ用意し、`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` を渡す。Flyway が起動時にスキーマを作る。
2. **バックエンド起動**（リポジトリルートで）:
   ```bash
   GCP_PROJECT_ID=<実テナントの projectId> ./gradlew bootRun   # :8080
   ```
3. ブラウザで `http://localhost:5173` を開く → 未ログインなら `/login` へ。テストユーザーでログインすると
   初回セットアップ（`POST /api/me:provision`）が走り、`/worlds` に「はじまりの世界」が 1 つ見える。
   世界を選ぶと `/worlds/{worldId}/bloodHorses` に遷移し、その世界の一覧が表示される。画面右上に
   直近レスポンスのステータス（未ログイン `401` → ログイン後 `200`）が出る。

### 自動のブラウザ E2E（`npm run test:e2e`）

上の手動手順と同じ通し（ログイン → 初回セットアップ → 世界の作成 → 馬一覧）は Playwright で自動化してある（#725）。

```bash
cd frontend
npm run test:e2e     # Docker が要る
```

Playwright の `webServer` が 3 つのプロセス（Firebase Auth Emulator :9099 / `./gradlew bootTestRun` :8080 /
`vite preview` :5173）と PostgreSQL（`docker compose up -d --wait`）を自分で起動するため、**実 Identity Platform
テナントも `.env.local` も要らない**。ログインは Auth Emulator に対して行い、Emulator が発行する未署名 ID トークンは
`src/test` に置いたテスト専用の `JwtDecoder` が受理する（`bootTestRun` は test runtime classpath でアプリを起動するので、
**本番成果物には一切手が入っていない**）。省くのは署名検証だけで、issuer / audience / 有効期限は本番と同じ検証を掛ける。

Emulator 向けの Firebase 設定は `frontend/.env.e2e`（**コミット済み**）が持つ。`vite build --mode e2e` がこれを読み、
`VITE_FIREBASE_AUTH_EMULATOR_HOST` があるときだけ `connectAuthEmulator` が走る。`apiKey` は Emulator が検証しないダミーで、
`VITE_FIREBASE_PROJECT_ID` は Emulator の `--project` とバックエンドの `GCP_PROJECT_ID`（いずれも `toy-box-e2e`）に揃えてある。
**3 箇所のどれかがずれると全リクエストが 401 になる。**

すでに `npm run dev` などが :5173 / :9099 / :8080 に居座っていると `reuseExistingServer` がそれを再利用してしまい、
実テナント向けのビルドが使われて `401` だけが直らない、という分かりにくい落ち方をする。先に止めてから回すこと。
射程・射程外と既知の地雷は `.claude/rules/testing.md` の「ブラウザ E2E」節が出所。

## スクリプト

| コマンド | 内容 |
|---|---|
| `npm run dev` | Vite dev server（:5173、`/api`→:8080 proxy） |
| `npm run build` | 型チェック（`tsc -b`）＋ 本番ビルド（`vite build`） |
| `npm run lint` | Biome で lint |
| `npm run format` | Biome で整形 |
| `npm run test` | Vitest（API クライアント・認証・ルーティングガード・一覧） |
| `npm run test:e2e` | Playwright のブラウザ E2E（Docker が要る。下記「自動のブラウザ E2E」） |
| `npm run emulator` | Firebase Auth Emulator 単体起動（:9099）。E2E のデバッグ用で、通常は `test:e2e` が自分で起動する |

CI（`.github/workflows/frontend.yml`）と lefthook の pre-commit が `frontend/**` の変更時に lint/build/test を回す（`test:e2e` はゲート外で、独立ワークフロー `.github/workflows/browser-e2e.yml` が回す）。

> **型検査の範囲は `tsconfig.json`（`src`）と `tsconfig.node.json`（`vite.config.ts` / `playwright.config.ts` / `e2e/`）の 2 つに分かれている。**
> `tsc` はプロジェクト参照を辿らないため、`build` は `tsc -b` で両方を検査する（#725）。node 側のファイルを増やしたら
> `tsconfig.node.json` の `include` に足すこと。足さないと Biome も Playwright も型を見ないため、無検査のまま残る。

> **`biome.json` の `$schema` はバージョン付き URL に戻さない**（#788）。`https://biomejs.dev/schemas/<version>/schema.json`
> を直書きすると、Dependabot が `@biomejs/biome` を上げるたびに版がずれて `npm run lint` が info を 1 件出す
> （パッチ版を落とした `2.5/schema.json` 等では回避できず、Biome は完全一致を要求する）。`node_modules` 内の
> スキーマを相対パスで指す形（Biome 公式ドキュメントの第一候補）ならインストール済みの実体を指すので常に一致する。
> なお設定が本体と非互換になったときは、この info ではなく `biome check` 自体がエラー（exit 1）で落ちる。

## 構成

```
frontend/src/
├── auth/       # Firebase 初期化（firebase.ts）と認証 Context（AuthContext.tsx）
├── api/        # fetch ラッパ（Bearer 付与・RFC 9457 problem+json 解釈）/ 世界 API / :provision
├── worlds/     # 世界一覧の state と変更操作（useWorlds.ts）
├── pages/      # LoginPage / WorldsPage / BloodHorseListPage / RequireAuth / RequireProvisioned
├── App.tsx     # React Router（/login, /worlds, /worlds/:worldId/bloodHorses〈要認証＋セットアップ済み〉）
└── main.tsx    # エントリ
```

## 現状の割り切り（MVP）

- **ローカルのみ**（Cloud Run デプロイ・イングレス公開/BFF・本番 CORS はスコープ外）。
- 認証確認・世界の CRUD・馬一覧の閲覧のみ。**書き込み画面（血統登録など）は後続**。
- 一覧の性・毛色・品種は現状 wire の英語定数名（`FEMALE` / `BAY` / `THOROUGHBRED`）を素で表示する（日本語ラベル化は follow-up）。
