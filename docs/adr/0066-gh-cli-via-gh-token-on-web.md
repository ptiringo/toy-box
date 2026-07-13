# 0066. Claude Code on the web で gh CLI を GH_TOKEN 認証で使う

- Status: Accepted
- Date: 2026-07-13
- Deciders: ptiringo

## Context（背景・課題）

[ADR-0065](0065-claude-code-on-the-web-support.md) は Claude Code on the web を**最小構成**（`./gradlew check` が緑）でサポートし、`gh` + `GH_TOKEN` による Projects・PR マージ運用は**スコープ外**（必要になったら別 Issue）とした。#628 がその follow-up で、クラウド／モバイルから `gh` で Issue/PR・Projects #4（Priority 運用）・PR マージ（`--admin`）を回せるようにしたい。

前提として次が確定している。

- **対象はクラウドセッションのみ**。ローカルの GitHub 操作は `gh` CLI 直接利用（[ADR-0001](0001-drop-github-mcp-use-gh-cli.md)）で、認証は不要（sandbox 外実行で解決済み）。web だけがギャップ。
- **`gh` はクラウド VM に未プリインストール**（#611 実測。`./gradlew check` には不要だった）。導入導線が要る。
- クラウドセッションは毎回 fresh clone で起動し、ローカルの環境変数・mise 管理ツールを引き継がない。セットアップスクリプト・環境変数・Custom 許可ドメインは**クラウド環境 UI 側に保存され、リポジトリにコミットできない**（ADR-0065 と同じ非ポータブル性）。

論点は「導入方式」と「認証方式」の 2 つ。

### 導入方式

- **mise.toml に一元化（採用）**: ツールバージョンの唯一の出所は `mise.toml`、という既存方針（CLAUDE.md）に従う。バックエンドは `mise registry gh` が指す `aqua:cli/cli`。
- devcontainer features / apt: バージョンを二重管理するため不採用（既存の他ツールと非対称になる）。

### 認証方式

- **UI の Secrets に自前 PAT を `GH_TOKEN` として登録し、gh に自動採用させる（採用）**: `gh` は env の `GH_TOKEN`（無ければ `GITHUB_TOKEN`）を自動採用するため `gh auth login` が不要。Projects #4 操作には `project` スコープが要る（`gh auth refresh -s project` 相当）ので PAT に `repo` + `project` を持たせる。
- `gh auth login`: クラウドセッションは fresh clone で揮発し対話認証のトークン保存先も残らないため不採用。
- `.env` ファイル: gitignore された `.env` は fresh clone に存在しない（構造的に持てない）ため不採用。

### 実測（この follow-up の実装セッション）

「env が非対話シェルに来るか」を**手を動かす前に実機確認**した（#611 の実測主義。落とし穴3＝env が setup フェーズに来ないパターンを疑う）。

- `echo ${GH_TOKEN:+set}` → **set**（`GITHUB_TOKEN` も）。**env は非対話シェルに届く**。したがって devcontainer 側の env 橋渡し（remoteEnv / containerEnv / post-create）は**不要**。
- `gh api user -q .login` → **`ptiringo`**。gh が `GH_TOKEN` を採用し user レベル API が通る。
- `gh api rate_limit -i` の `X-Oauth-Scopes` → `repo, project, read:org, ...`。**`repo` + `project` スコープを確認**。
- `gh auth status` は「token is invalid」と表示するが、実 API 呼び出しは通る（gh 独自の検証ヒューリスティックの表示に過ぎない）。
- ただし**実装セッション固有のプロキシ制限**として、`gh issue list` / `gh pr list` / `gh project view`（GraphQL）と repo REST はこのセッションでは 403 だった（egress プロキシが GraphQL/repo REST を Claude GitHub App 経由にゲートしていたため）。ユーザーが対話的に開くセッション（GitHub が Trusted・自前 PAT）ではこの制限はない見込み。差の詳細と実測表は [docs/claude-code-on-the-web.md](../claude-code-on-the-web.md) の「gh CLI（GitHub 操作）」節に記録した。

## Decision（決定）

Claude Code on the web のクラウドセッションで `gh` CLI を使えるようにする。

- **導入**: `gh` を `mise.toml` の `[tools]` に `aqua:cli/cli`（バージョンピン）で追加する。`scripts/web-setup.sh` が toolchain 検証の後に `mise install aqua:cli/cli` を **best-effort** で実行する（`./gradlew check` のクリティカルパスではないため、取得失敗しても setup を止めない）。PATH は既存の `session-start-mise.sh`（`mise hook-env`）が注入する。
- **認証**: UI の Secrets に自前 PAT を `GH_TOKEN` として登録し、gh に自動採用させる（`gh auth login` はしない）。PAT は `repo` + `project` スコープ（Projects #4 運用のため）。`.env` は使わない。env は非対話シェルに届くことを実測済みのため、env 橋渡しのフォールバックは入れない。
- **許可ドメイン**: `gh` の通信先（api.github.com / github.com / objects.githubusercontent.com）は GitHub でデフォルト Trusted のため Custom 追加は不要。
- **非ポータブル設定の記録**: UI 側にしか置けない `GH_TOKEN` の登録手順・PAT スコープ・実測結果は `docs/claude-code-on-the-web.md` に残す（ADR-0065 と同じ方針。値は UI・手順はコミット）。
- ローカルの GitHub 操作方針（[ADR-0001](0001-drop-github-mcp-use-gh-cli.md)）と整合する（どちらも MCP でなく `gh` CLI。差は認証だけで、ローカルは sandbox 外実行、web は `GH_TOKEN`）。

## Consequences（結果・影響）

- クラウド／モバイルから `gh` で Issue/PR・Projects #4・PR マージを回せるようになる。GitHub 操作の出所がローカル／web ともに `gh` CLI で揃う。
- `GH_TOKEN`（自前 PAT）は UI 側に保存され共有できないため、各自が `docs/claude-code-on-the-web.md` の手順で PAT を発行・登録する必要がある。この非ポータブル性はドキュメントによる再現手順で補償する（ADR-0065 と同じトレードオフ）。
- PAT は自前発行のため、スコープ（`repo` + `project`）と有効期限の管理は各自の責任になる。最小権限で発行し、不要になれば失効させる。
- `gh` を best-effort 導入にしたことで、GitHub 取得の一時失敗やレート制限で `./gradlew check` の土台を止めない。gh 無しでも check は緑になる。
- 実装セッションでは egress プロキシの制限で repo/org/GraphQL 操作が 403 だった。ユーザーの対話セッションでの `gh issue list` 等の疎通は、初回に実測して `docs/claude-code-on-the-web.md` の表を確定する運用とする。
