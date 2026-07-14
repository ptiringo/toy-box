# 0066. Claude Code on the web で gh CLI を使う（認証は /web-setup 優先）

- Status: Accepted
- Date: 2026-07-13
- Deciders: ptiringo
- 改訂: 2026-07-14 — 初版は「UI の環境変数に自前 PAT を `GH_TOKEN` として登録」を第一の認証方式としたが、環境変数が**専用の秘密ストアではなく平文で保存され、環境を編集できる人に見える**（公式明記）ことを踏まえ、`/web-setup`（マネージド同期）を第一候補に改める。PAT は `/web-setup` で届かないスコープに限ったフォールバックへ格下げ。

## Context（背景・課題）

[ADR-0065](0065-claude-code-on-the-web-support.md) は Claude Code on the web を**最小構成**（`./gradlew check` が緑）でサポートし、`gh` による Projects・PR マージ運用は**スコープ外**（必要になったら別 Issue）とした。#628 がその follow-up で、クラウド／モバイルから `gh` で Issue/PR・Projects #4（Priority 運用）・PR マージ（`--admin`）を回せるようにしたい。

前提として次が確定している。

- **対象はクラウドセッションのみ**。ローカルの GitHub 操作は `gh` CLI 直接利用（[ADR-0001](0001-drop-github-mcp-use-gh-cli.md)）で、認証は不要（sandbox 外実行で解決済み）。web だけがギャップ。
- **`gh` はクラウド VM に未プリインストール**（#611 実測。`./gradlew check` には不要だった）。導入導線が要る。
- クラウドセッションは毎回 fresh clone で起動し、ローカルの環境変数・mise 管理ツールを引き継がない。セットアップスクリプト・環境変数・Custom 許可ドメインは**クラウド環境 UI 側に保存され、リポジトリにコミットできない**（ADR-0065 と同じ非ポータブル性）。
- **環境変数は秘密ストアではない**（公式明記）: "A dedicated secrets store is not yet available. Both environment variables and setup scripts are stored in the environment configuration, **visible to anyone who can edit that environment**." したがって長命・広スコープの PAT を環境変数に置くのは at-rest のセキュリティ姿勢が弱い。

論点は「導入方式」と「認証方式」の 2 つ。

### 導入方式

- **mise.toml に一元化（採用）**: ツールバージョンの唯一の出所は `mise.toml`、という既存方針（CLAUDE.md）に従う。バックエンドは `mise registry gh` が指す `aqua:cli/cli`。
- devcontainer features / apt: バージョンを二重管理するため不採用（既存の他ツールと非対称になる）。

### 認証方式

**平文の環境変数に長命 PAT を置かない**を軸に、次の優先順位を採る。

- **第一候補: `/web-setup`（マネージド。採用）**: ローカルの Claude Code CLI で `/web-setup` を実行し、ローカルの `gh` トークンを **Claude アカウントに紐付ける**（"reads your local `gh` token, links it to your Claude account"）。資格情報は**平文 env config には入らない**。Projects #4 操作には `project` スコープが要るので、事前にローカルで `gh auth refresh -s project` してから `/web-setup` する。
- **フォールバック: fine-grained・短命 PAT を環境変数に（限定採用）**: `/web-setup` で届かないスコープ（例: Projects の GraphQL）が残る場合に限り、**対象リポジトリ 1 つ・権限は Contents / Issues / Pull requests / Projects のみ・短い有効期限**の fine-grained PAT を発行し `GH_TOKEN` に置く。gh は env の `GH_TOKEN`（無ければ `GITHUB_TOKEN`）を自動採用するため `gh auth login` は不要。平文保存・編集者可視を許容したうえで、最小権限＋短命＋ローテーションで被害範囲を絞る。
- **不採用**: `gh auth login`（fresh clone で対話トークンの保存先が揮発）。長命・広スコープ PAT を環境変数へ常設（at-rest が弱い。上記フォールバックは最小権限・短命に限定）。
- **組み込み GitHub App のみ**では git（clone/push）は資格情報プロキシが担うが、`gh` の repo/org/Projects（GraphQL）API までは届かない見込み（下記実測）。

### 実測（この follow-up の実装セッション）

「env が非対話シェルに来るか」を**手を動かす前に実機確認**した（#611 の実測主義）。ただしこの実装セッションは自動起動の管理環境で、`GH_TOKEN` は**プロキシ注入の資格情報**（自前 PAT でも `/web-setup` トークンでもない）であり、`/web-setup` は実行できなかった点に注意。

- `echo ${GH_TOKEN:+set}` → **set**（`GITHUB_TOKEN` も）。**env は非対話シェルに届く**（env 橋渡しは不要）。
- `gh api user -q .login` → **`ptiringo`**。gh が env の `GH_TOKEN` を採用し user レベル API が通る。
- `gh api rate_limit -i` の `X-Oauth-Scopes` → `repo, project, read:org, ...`。
- `gh auth status` は「token is invalid」と表示するが実 API は通る（gh のヒューリスティック表示に過ぎない）。
- `gh issue list` / `gh pr list` / `gh project view`（GraphQL）と repo REST は**このセッションでは 403**（egress プロキシが GraphQL/repo REST を Claude GitHub App 経由にゲート。実装セッション固有）。

**`/web-setup` が Projects 操作まで満たすかは自動セッションでは検証できていない**。ユーザーの対話セッションで実測し `docs/claude-code-on-the-web.md` の「gh CLI（GitHub 操作）」節を確定する。

## Decision（決定）

Claude Code on the web のクラウドセッションで `gh` CLI を使えるようにする。

- **導入**: `gh` を `mise.toml` の `[tools]` に `aqua:cli/cli`（バージョンピン）で追加する。`scripts/web-setup.sh` が toolchain 検証の後に `mise install aqua:cli/cli` を **best-effort** で実行する（`./gradlew check` のクリティカルパスではないため、取得失敗しても setup を止めない）。PATH は既存の `session-start-mise.sh`（`mise hook-env`）が注入する。CI では gh を使わないため `mise.ci.toml` の `disable_tools` で `--locked` 解決の対象外にする。
- **認証**: **`/web-setup` を第一候補**とし、ローカルの `gh` トークン（`project` スコープ付き）を Claude アカウントにマネージド同期する。`/web-setup` で届かないスコープが残る場合に限り、**fine-grained・短命 PAT を環境変数 `GH_TOKEN`** に置く（最小権限・短命・ローテーション前提）。**長命・広スコープ PAT を環境変数に常設しない**（環境変数は平文・編集者可視で秘密ストアではないため）。
- **許可ドメイン**: `gh` の通信先（api.github.com / github.com / objects.githubusercontent.com）は GitHub でデフォルト Trusted のため Custom 追加は不要。
- **設定手順の記録**: UI 側にしか置けない設定（`/web-setup` 手順・フォールバック PAT のスコープ・実測結果）は `docs/claude-code-on-the-web.md` に残す（ADR-0065 と同じ方針。値は UI・手順はコミット）。
- ローカルの GitHub 操作方針（[ADR-0001](0001-drop-github-mcp-use-gh-cli.md)）と整合する（どちらも MCP でなく `gh` CLI。差は認証だけで、ローカルは sandbox 外実行、web は `/web-setup` または `GH_TOKEN`）。

## Consequences（結果・影響）

- クラウド／モバイルから `gh` で Issue/PR・Projects #4・PR マージを回せるようになる。GitHub 操作の出所がローカル／web ともに `gh` CLI で揃う。
- **at-rest の姿勢が改善**する: 第一候補の `/web-setup` は資格情報を Claude アカウントにマネージド保存し、平文 env config に PAT を置かない。フォールバック PAT も最小権限・短命に限定し、常設の長命秘密を避ける。
- `/web-setup` が Projects（GraphQL）まで満たすかは未検証。対話セッションで実測して確定し、満たさない操作があればその操作に限り fine-grained・短命 PAT で補う。実測結果は `docs/claude-code-on-the-web.md` に反映する運用とする。
- 認証は各自のローカル環境・Claude アカウントに依存し共有できない（非ポータブル）。ドキュメントの再現手順で補償する（ADR-0065 と同じトレードオフ）。ローカルの秘密は引き続き fnox + 1Password で解決し、web の割り切りはそちらに波及させない。
- `gh` を best-effort 導入にしたことで、GitHub 取得の一時失敗やレート制限で `./gradlew check` の土台を止めない。gh 無しでも check は緑になる。
