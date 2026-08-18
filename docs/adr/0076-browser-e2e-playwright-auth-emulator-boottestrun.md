# 0076. ブラウザ E2E を Playwright + Auth Emulator + bootTestRun で組み、ゲート外の独立ワークフローで回す

- Status: Accepted
- Date: 2026-08-18
- Deciders: Matsui

## Context（背景・課題）

`frontend/`（Vite + React の SPA）にはブラウザを通した E2E が無く、画面の退行を機械的に検出できなかった。#693（react-router の minor 更新）と #714（世界の選択・作成）はいずれも「実ブラウザでの通し確認は未実施」のままマージされている。#714 で画面が `/worlds` と `/worlds/:worldId/bloodHorses` の 2 枚 + ガード 2 段に増え、空白は広がっていた。自動化されていない確認手順は引かれない。とくに #699（React 19 + react-router v8 の major 移行）は minor と違って手動確認で押し切れないため、その前に通しの退行検出が要る（#725）。

論点は 4 つあった。

**1. ツール。** Playwright / Cypress / vitest browser mode を比べた。Cypress は CI 実装が重く、vitest browser mode は内部で Playwright provider を使うため結局 Playwright に依存する。複数プロセス（フロント・バックエンド・Emulator）を起動・待機する `webServer` を config だけで書ける点も含め、Playwright が最も素直だった。

**2. ログインをどう通すか。** 「実ログインを含む通し」を射程に入れると決めた以上、Firebase のサインイン処理そのものが動く必要がある。候補は 2 つ。

- **実 Identity Platform テナントのテスト用ユーザー**: 署名検証まで含めて本物になる。ただし CI に GCP 資格情報とネットワーク到達性が要り、テナント上のテストユーザーという共有可変状態を運用し続けることになる。
- **Firebase Auth Emulator**: 自己完結する。ただし Emulator が出す ID トークンは**未署名**（Firebase 公式が "the Authentication emulator issues unsigned ID tokens" と明記しており仕様）なので、バックエンドが署名検証をしないで受理する経路を持つ必要がある。

後者を採るとき、**その経路を本番成果物（`src/main`）に入れてしまうと認証が無防備になる**（誰でも任意の `sub` を名乗れる）。これが最大の制約だった。

**3. どこで起動するか。** 上の制約を `./gradlew bootTestRun`（Spring Boot Gradle plugin が登録する、**test runtime classpath でアプリを起動する**タスク）が解く。テスト専用の `JwtDecoder` を `src/test` に置いたまま効かせられるため、`src/main` には 1 行も入らない。加えて `JwtDecoder` Bean が居ると Spring Boot の自動構成（`@ConditionalOnMissingBean`）が下がるので、起動時の OIDC discovery（GCP への通信）も走らない。既存の `TestJwtDecoderConfiguration` とまったく同じ手であり、新しい概念を持ち込まない。

**4. ゲートに入れるか。** API E2E は同じ問いに対し ADR-0056 で「遅く探索的なため `check` / pre-push / ArchUnit / Kover のいずれの対象にもしない」と答えている。ブラウザ E2E は 4 プロセス（Emulator / `bootTestRun` / `vite preview` / PostgreSQL）を起動するぶんさらに重く、Docker を要求する。

## Decision（決定）

| 論点 | 決定 |
|---|---|
| 射程 | **実ログインを含む通し**（Firebase のサインイン処理そのものを検証対象に含める） |
| ツール | **Playwright**（`frontend/e2e/`、`npm run test:e2e`） |
| 認証 | **Firebase Auth Emulator**。実テナントは使わない |
| バックエンドの起動 | **`./gradlew bootTestRun`**。`bootRun` は使わない |
| 署名検証の扱い | **`src/test` の `EmulatorJwtDecoder` が署名検証だけを省く**。issuer / audience / 有効期限は本番と同じ validator を掛ける |
| シナリオ範囲 | **ハッピーパス 1 本**。分岐・エラー表示は jsdom 側（vitest）が担保する |
| フロントの配信 | **`vite preview`（本番ビルド成果物）**。dev server ではない |
| 実行場所 | **独立ワークフロー `browser-e2e.yml`**（PR / main push / 手動）。**`check` / pre-push のゲートには入れない** |
| CI の paths | `paths-ignore` で `docs/**` / `infra/**` / `*.md` だけ落とす。**`frontend/**` には絞らない** |

**`EmulatorJwtDecoder` を `src/main` へ移してはならない。** これがこの構成の中核的な制約で、破ると本番の認証が無防備になる。クラスの KDoc と `.claude/rules/testing.md` に明記してある。

省くのは署名検証だけである。issuer / audience を本番と同じ経路（`application.yml` の値）で検証するので、「フロントの `projectId` とバックの `GCP_PROJECT_ID` がずれてトークンが 401 になる」という実際に起きた事故は E2E の射程に入る。

CI の paths を `frontend/**` に絞らないのは意図的で、#705 のようにバックエンドが API のパスを変えるとフロントが壊れるため、バックエンドだけの変更でも回す必要がある。

### 射程外（明示）

この E2E が**守らないもの**。後から「E2E が通っているから安全」と誤読しないために明記する。

- **JWKS 取得と RS256 署名検証そのもの**。Emulator のトークンは未署名で、構造的に検証できない。ここは**担保の分担を書き分けておく**:
  - 「**署名が検証できないトークンは 401 になる**」というフィルタ層の振る舞いは `SecurityConfigTest` が担保する。ただし同テストも `JwtDecoder` を `TestJwtDecoderConfiguration` の HS256 実装へ差し替えており、**実 JWKS は引かない**（`@WebMvcTest` の slice が認証フィルタを無効化しているため、フィルタ層を通す役割を負っているのがこのテストである）。
  - したがって「**Identity Platform の JWKS を取得して RS256 で署名を検証する**」経路そのものを実行しているテストはリポジトリに存在しない。ここを担保するのは**本番運用のみ**である。
- **実 Identity Platform テナントとの疎通**（issuer の OIDC discovery、実テナントのユーザー管理）
- **WorldsPage の改名・削除**。ハッピーパス 1 本に絞ったため。jsdom 側の `WorldsPage.test.tsx` が担保する
- **エラー表示・失敗分岐**（`RequireProvisioned` の error 状態など）。jsdom 側が担保する

## Consequences（結果・影響）

得たもの:

- 実ログインを含む通しが自動で回るようになり、#699 のような major 移行に退行検出を先に置ける。
- CI ジョブが**自己完結する**。GCP 資格情報もネットワーク到達性も要らず、テナント上の共有可変状態も持たない。テストユーザーは実行ごとにユニークな email で Emulator に作るため（別 email = 別 `sub` = 別アカウント = 別世界）、DB も Emulator もリセットせずに済み、後始末の仕組みを持たなくてよい。
- 本番成果物は無傷のまま。`frontend/src/auth/firebase.ts` の `VITE_FIREBASE_AUTH_EMULATOR_HOST` 分岐だけが唯一のプロダクションコード変更で、これは Firebase 公式の標準手法であり、env が無ければ従来どおり実テナントへ向かう。

引き受けたもの:

- **署名検証がこの経路の射程に入らない**。上記「射程外」のとおり、フィルタ層の振る舞い（検証できないトークンは 401）は `SecurityConfigTest` に、JWKS 取得と RS256 検証そのものは本番運用に委ねる。
- **ローカル実行に Docker が要る**（PostgreSQL）。`bootTestRun` では `spring-boot-docker-compose` が `developmentOnly` 依存で test runtime classpath に載らないため、`compose.yaml` の自動配線が効かない。DB は `docker compose up -d --wait` で先に立て、datasource を env で明示供給する（本番 Cloud Run と同じ注入経路）。
- **`GCP_PROJECT_ID` / Emulator の `--project` / `VITE_FIREBASE_PROJECT_ID` の 3 箇所を一致させ続ける必要がある**。ずれると全トークンが 401 になる（fail closed）。
- **ゲート外なので、赤いまま気づかずマージされうる**。API E2E（ADR-0056）と同じ割り切りで、網羅はここで広げず内側リング（vitest + jsdom、`@WebMvcTest`）で担保する。

運用上の結論は `.claude/rules/testing.md`「ブラウザ E2E（frontend・ゲート外）」（射程・射程外・実測済みの地雷）と `frontend/README.md`（人間向けの手順）に置いた。ここには経緯だけを残す。

## 関連

- [ADR-0056](0056-drop-karate-native-resttestclient-e2e.md) — API E2E をゲート外の独立ワークフローで回す決定。本 ADR はその扱いに揃えた
- [ADR-0039](0039-e2e-api-tests-with-karate.md) — API E2E に Karate を採用した決定（ADR-0056 で撤退）
- [ADR-0064](0064-authn-via-identity-platform-authz-in-app.md) — 認証を Identity Platform に委譲する決定
- [ADR-0067](0067-per-player-world-tenant-isolation.md) — 世界（テナント）分離。`/worlds/{worldId}/...` の由来
- #725 — 本 E2E の実装 Issue。#693 / #714 が空白を顕在化させ、#699 が最も要る場面
