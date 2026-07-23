# toy-box フロントエンド（軽種馬シミュレータ）

ログインしたユーザーが実 Identity Platform にログインし、保護 API `GET /api/bloodHorses` を叩いて軽種馬一覧を見る軽量 SPA（Vite + React + TypeScript）。認証・認可を「目で見て触れる」ことが主眼の MVP（#612）。

## 前提

- **Node**: `mise.toml` の `node`（22 系）。リポジトリルートで `mise install` すれば入る。
- **実 Identity Platform テナント**: ログインは Firebase Auth JS SDK 経由で実テナントに対して行う（エミュレータは使わない）。email/password サインインを有効化し、テストユーザーを1人作っておく。

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
3. ブラウザで `http://localhost:5173` を開く → 未ログインなら `/login` へ。テストユーザーでログイン → `/bloodHorses` に遷移し一覧が表示される。画面右上に直近レスポンスのステータス（未ログイン `401` → ログイン後 `200`）が出る。

## スクリプト

| コマンド | 内容 |
|---|---|
| `npm run dev` | Vite dev server（:5173、`/api`→:8080 proxy） |
| `npm run build` | 型チェック（`tsc`）＋ 本番ビルド（`vite build`） |
| `npm run lint` | Biome で lint |
| `npm run format` | Biome で整形 |
| `npm run test` | Vitest（API クライアント・認証・ルーティングガード・一覧） |

CI（`.github/workflows/frontend.yml`）と lefthook の pre-commit が `frontend/**` の変更時に lint/build/test を回す。

## 構成

```
frontend/src/
├── auth/       # Firebase 初期化（firebase.ts）と認証 Context（AuthContext.tsx）
├── api/        # fetch ラッパ（Bearer 付与・RFC 9457 problem+json 解釈・401 判定）
├── pages/      # LoginPage / BloodHorseListPage / RequireAuth（認証ガード）
├── App.tsx     # React Router（/login, /bloodHorses〈要認証〉）
└── main.tsx    # エントリ
```

## 現状の割り切り（MVP）

- **ローカルのみ**（Cloud Run デプロイ・イングレス公開/BFF・本番 CORS はスコープ外）。
- 認証確認 + 馬一覧のみ。書き込み画面・認可 403 体験（#606 / #607）は後続。
- 一覧の性・毛色・品種は現状 wire の英語定数名（`FEMALE` / `BAY` / `THOROUGHBRED`）を素で表示する（日本語ラベル化は follow-up）。
