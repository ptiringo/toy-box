# Claude Code on the web セットアップ手順

[Claude Code on the web](https://code.claude.com/docs/ja/claude-code-on-the-web) は Anthropic 管理のクラウド VM でセッションを実行する。本リポジトリをクラウド／モバイルから回すための設定手順を記録する。

> **これは Dev Container（`.devcontainer/`）とは別の実行環境**である。web 環境は containers.dev の devcontainer ではなく Anthropic 管理 VM で、`devcontainer.json` / features / firewall は使わない。共通するのは「ツールの出所は `mise.toml`」という一点のみ。

## ゴール

クラウドセッションで `./gradlew check`（ビルド + テスト + Testcontainers + カバレッジゲート）が緑になること。加えて次を **best-effort** で有効化する（いずれも失敗しても check 緑には影響しない補助）:

- kotlin-lsp（編集時診断・コードナビ。#627。手順は「kotlin-lsp の有効化」節）。
- `gh` CLI（Issue/PR・Projects #4・PR マージ。#628。手順は「gh CLI（GitHub 操作）」節）。

terraform MCP（`docker run`）のフル同等化はスコープ外（本ドキュメント末尾「スコープ外」参照）。

## 前提（クラウド VM のプリインストール）

公式ドキュメント（2026-07 時点）で確認済み: OpenJDK 21・Maven・Gradle・Docker / docker compose・PostgreSQL 16・Node。リソースは 4 vCPU / 16 GB RAM / 30 GB disk。

本リポジトリの Gradle toolchain は `languageVersion = 25`（`build.gradle.kts`）。素の JDK 21 では toolchain auto-detection が満たせないため、**mise で Temurin 25 を供給する**。

## UI 側に設定する内容

Claude Code on the web では、セットアップスクリプトの登録・環境変数・Custom 許可ドメインは**クラウド環境 UI 側に保存され、リポジトリにコミットできない**。そのため以下を「UI に貼る内容」として手動で設定する（この非ポータブル性の記録は [ADR-0065](adr/0065-claude-code-on-the-web-support.md)）。

### 1. セットアップスクリプト

UI の「セットアップスクリプト」欄に次を貼る（本体はリポジトリ管理の `scripts/web-setup.sh`）:

```bash
bash "$(find / -type f -name web-setup.sh -path '*/scripts/web-setup.sh' 2>/dev/null | head -n1)"
```

`scripts/web-setup.sh` は mise を導入し、`mise install java`（JDK 25 のみ）を実行して toolchain を検証する。全ツールは入れない（`./gradlew check` に不要なため）。検証の `mise exec` は **java にスコープする**（`mise exec java -- ...`）。ツール未指定の `mise exec -- ...` / `mise install`（無指定）は mise.toml の全ツールを auto-install してしまい、GitHub API のレート制限（未認証 60/h）等で落ちるため（実測）、ツールは常に**名指し**で入れる。検証の後に kotlin-lsp を `mise install http:kotlin-lsp` で **best-effort** 導入する（下記「kotlin-lsp の有効化」。失敗は握りつぶして続行し check 緑には影響させない）。

> **TLS 傍受プロキシの CA を JDK に信頼させる（重要）**: クラウドの egress は TLS を終端・再署名する [HTTP/HTTPS セキュリティプロキシ](https://code.claude.com/docs/en/claude-code-on-the-web#security-proxy)（MITM）経由。`curl` / `apt` はシステム CA ストア（プロキシ CA 込み）で通るが、mise 導入の Temurin は**独自 cacerts** を使うため、素のままだと Gradle の HTTPS ダウンロード（`services.gradle.org` 等）が `javax.net.ssl.SSLHandshakeException: PKIX path building failed` で落ちる（実測）。`scripts/web-setup.sh` はシステム CA バンドル（`/etc/ssl/certs/ca-certificates.crt`）を JDK の `cacerts` に取り込んでこれを解消する（バンドルは複数証明書の連結で `keytool` は先頭 1 件しか読まないため分割して個別 import）。プロキシ CA のパスや JVM 向けの信頼手順は公式未文書化のため、システムバンドル全体を取り込む方式にしている。

> **なぜ `bash scripts/web-setup.sh` ではなく `find` で絶対パス解決するか**: セットアップスクリプトの実行時 CWD は公式ドキュメントに記載がなく、リポジトリルートである保証がない。repo 相対パスで呼ぶと `exit 127: No such file or directory` になる（実測）。リポジトリのクローン自体は setup 実行時点で存在する（"Cloud sessions start from a fresh clone ... Everything committed is available"）が、クローン先パスも未文書化のため、コミット済みヘルパーを `find` で引いて絶対パスで実行する。ヘルパー側は自分の位置からリポジトリルートへ `cd` するので、以降の `mise trust` / `mise install`（`mise.toml` を読む）は正しく走る。

> **なぜ SessionStart フックでなく setup スクリプトか**: 公式は「ランタイム/CLI の導入は setup スクリプト、両環境で要るプロジェクト設定は SessionStart フック」を推奨している。mise + JDK の導入は前者にあたる。加えて SessionStart フックに寄せると、初回セッションで mise 未導入のまま既存の `session-start-mise.sh`（`mise hook-env`）が空振りし PATH が注入されない順序問題が起きる。setup スクリプトは Claude Code 起動前に完走するのでこれを避けられる。

### 2. Custom 許可ドメイン

Gradle 依存解決（Maven Central・`plugins.gradle.org`・`services.gradle.org`・`spring.io`・`kotlinlang.org` 等）はデフォルト Trusted で通る。**Custom に足す必要があるもの**（すべて実クラウドで確認済み）:

| ドメイン | 用途 |
|---------|------|
| `mise.run` | mise インストールスクリプトの配信元（`curl https://mise.run \| sh`） |
| `mise.jdx.dev` | mise バイナリの取得先（GitHub が使えない場合のフォールバック） |
| `mise-versions.jdx.dev` | `mise install` のバージョン解決 |
| `mise-java.jdx.dev` | `mise install java` が JVM メタデータ（tar の所在）を引く先 |
| `production.cloudfront.docker.com` | **Docker Hub の blob CDN**。Testcontainers のイメージ取得（`postgres:17-alpine` / Ryuk）に必須 |
| `download-cdn.jetbrains.com` | **kotlin-lsp（JetBrains 公式 Language Server）の配信元**。`mise install http:kotlin-lsp` の取得先。無いと best-effort 導入が失敗し編集時診断が無効になる（`./gradlew check` には影響しない）。値の出所は `.devcontainer/allowed-domains.txt`（コミット共有） |

- **Docker Hub はデフォルト Trusted ではない**（当初そう見込んでいたが実測で否定された）。レジストリ API（`registry-1.docker.io`）までは通るが、**blob 取得が CDN で 403 になり** Testcontainers が `ContainerFetchException` で落ちる。CDN ホストを Custom に足すこと（`registry-1.docker.io` / `auth.docker.io` も 403 が出るなら併せて足す）。
- GitHub（`github.com` / `objects.githubusercontent.com`）は Trusted で追加不要（mise バイナリ・チェックサム・JDK tar 実体はそこから来る）。
- `api.adoptium.net` は**不要**（mise は `mise-java.jdx.dev` 経由で解決する）。

> これらは `.devcontainer/allowed-domains.txt` には**無い web 固有の要求**を含む。devcontainer は mise を feature で導入するため `mise.run` / `mise-java.jdx.dev` を使わないが、web は素の VM に `curl mise.run` でブートストラップするため追加で要る。

> **クローン先**: 実測でリポジトリは `/home/user/toy-box` に置かれた。ただしこのパスは公式未文書化なので UI では `find` による絶対パス解決（上記「1. セットアップスクリプト」）に依存する。

### 3. 環境変数

**UTF-8 ロケールを必ず設定する**（`./gradlew check` に必須）:

```
LANG=C.utf8
LC_ALL=C.utf8
```

クラウド VM の既定ロケールは `POSIX`(C) で、JVM の `sun.jnu.encoding`（ファイル名エンコーディング）が非 UTF-8 になる。本リポジトリは**テストメソッド名を日本語で書く**規約のため、そこから生成される `.class` のパスを書き出せず、Kotlin コンパイラが `InvalidPathException: Malformed input or input contains unmappable characters` を伴う内部エラーで落ちる（実測。`:compileTestKotlin` が失敗）。`sun.jnu.encoding` は OS ロケール由来で `-D` 指定が効かないことがあるため、**ロケール自体を UTF-8 にする**のが確実。

`scripts/web-setup.sh` も自身の JVM 実行のために UTF-8 ロケールを export するが、**セッション側に効かせるにはこの UI 環境変数の設定が要る**。

> **GitHub 認証（`gh`）はここ（環境変数）ではなく `/web-setup` を第一候補にする。** 詳細は本ドキュメント末尾の「gh CLI（GitHub 操作）」節を参照。環境変数への PAT 登録は `/web-setup` で足りない場合のフォールバックに限る（**環境変数は専用の秘密ストアではなく、環境を編集できる人に平文で見える**ため）。

## TLS 傍受プロキシと JVM（実測メモ）

クラウドの egress は TLS を再署名する[セキュリティプロキシ](https://code.claude.com/docs/en/claude-code-on-the-web#security-proxy)経由で、証明書の発行者は `O = Anthropic, CN = Egress Gateway SDS Issuing CA (production)`。

- **セッション**には CA 入り truststore が `JAVA_TOOL_OPTIONS`（`-Djavax.net.ssl.trustStore=…/java-truststore.p12` + プロキシ設定）で渡るため、JVM は素で TLS を通せる。CA バンドルの実体は `NODE_EXTRA_CA_CERTS` / `SSL_CERT_FILE` / `CURL_CA_BUNDLE` / `REQUESTS_CA_BUNDLE` が指すファイル。
- **setup スクリプトの実行コンテキストには `JAVA_TOOL_OPTIONS` が渡らない**。そのため mise 導入直後の Temurin は独自 cacerts のままで、Gradle の HTTPS ダウンロードが `PKIX path building failed` で落ちる。`scripts/web-setup.sh` はプロキシ CA を JDK の cacerts に取り込んでこれを解消する。

## Docker デーモンは自動起動しない

クラウド VM は Docker / docker compose を同梱するが、**デーモンが自動起動しない**。`/var/run/docker.sock` が無いため Testcontainers が `Could not find a valid Docker environment` で落ち、Postgres 契約テストと `@SpringBootTest` 系が全滅する（実測）。

これは**毎セッション**必要なので、環境キャッシュがあるとスキップされる setup スクリプトではなく **SessionStart フック** `.claude/hooks/session-start-docker.sh` が `dockerd` を起動する。`CLAUDE_CODE_REMOTE` でクラウドセッション限定にガードしており、ローカル（macOS 等）では何もしない。

手動で起こす場合は `sudo dockerd &` を `./gradlew check` の前に実行する。

## 検証結果（2026-07-12 実クラウドで確認済み）

本ドキュメントの設定で **`./gradlew check` が BUILD SUCCESSFUL**（ktfmt / detekt / Kover ゲート・Testcontainers Postgres 契約テストを含む全チェック通過）。

- PATH は既存の `.claude/hooks/session-start-mise.sh`（SessionStart フック）が `mise hook-env` で注入するので、`mise exec --` プレフィックス無しで `./gradlew` が動く。
- 追加のホストが 403 で落ちた場合は、そのホストを Custom 許可ドメインに足して再実行し、上記の表を実測値に更新すること。

> **既知のノイズ**: クラウドでビルドすると mise が `mise.lock` に musl プラットフォーム項目などを自動追記することがある。環境固有の付随的な差分なのでコミットしない。

## kotlin-lsp の有効化（#627）

> **クラウド未実測**: 以下の配線は入れたが、実クラウドセッションでの動作は未確認。実測して本節を確定すること。

Claude Code の `kotlin-lsp@claude-plugins-official` プラグイン（`.claude/settings.json` の `enabledPlugins` で有効化済み・コミット共有）は、PATH 上の `kotlin-lsp --stdio` を起動して**編集時のリアルタイム診断・コードナビ**を提供する（採否・供給は [ADR-0046](adr/0046-adopt-kotlin-lsp-plugin.md)）。ゲート（detekt / ArchUnit / gradle check）を置き換えない補助操舵。

- **導入**: `scripts/web-setup.sh` が toolchain 検証の後に `mise install http:kotlin-lsp` を **best-effort** で実行する（`http:` バックエンドで `download-cdn.jetbrains.com` から取得）。取得失敗しても setup は止めず、`./gradlew check` の緑には影響しない。
- **要件（UI 側）**: 上記「2. Custom 許可ドメイン」の表に `download-cdn.jetbrains.com` を追加すること。無いと best-effort 導入が失敗し編集時診断が無効のままになる。
- **PATH**: バイナリは既存の `session-start-mise.sh`（`mise hook-env`）が注入する PATH に載る想定。プラグインの取り込みは初回にユーザーの信頼承認を経る。

### 実測で確定すべき点

- `download-cdn.jetbrains.com` だけで足りるか（別ホストが 403 なら Custom に追記し表を更新）。
- mise の `http:` バックエンドのダウンロードが TLS 傍受プロキシ越しに通るか（mise はシステム CA を使う想定だが未確認）。
- **Linux x64 の kotlin-lsp が起動・動作するか**（[ADR-0046](adr/0046-adopt-kotlin-lsp-plugin.md) は Linux を未実機検証と明記。クラウド VM は Linux）。
- ランチャ `kotlin-lsp.sh` がセッションの JDK 25（mise hook-env 注入 PATH）を使えるか。ランタイムはセッションの `JAVA_TOOL_OPTIONS`（プロキシ CA 入り truststore）が効く想定。

## gh CLI（GitHub 操作）（#628）

クラウド／モバイルから GitHub の Issue/PR・Projects #4（Priority 運用）・PR マージ（`--admin` マージ）を `gh` で回せるようにする。ローカルの GitHub 操作方針（MCP でなく `gh` CLI = [ADR-0001](adr/0001-drop-github-mcp-use-gh-cli.md)）とクラウドを揃える follow-up。設計・認証運用は [ADR-0066](adr/0066-gh-cli-via-gh-token-on-web.md)。

### 導入（`gh` バイナリ）

- `gh` は `mise.toml`（`aqua:cli/cli`）に一元管理する（features / apt は使わない = ツールバージョンの唯一の出所は `mise.toml`）。`scripts/web-setup.sh` が toolchain 検証の後に `mise install aqua:cli/cli` を **best-effort** で実行する（失敗しても setup は止めず check 緑に影響しない）。PATH は既存の `session-start-mise.sh`（`mise hook-env`）が注入するので `gh` を素で呼べる。
- **許可ドメイン**: `gh`（api.github.com / github.com / objects.githubusercontent.com）は GitHub なので**デフォルト Trusted・Custom 追加不要**（`mise install aqua:cli/cli` の api 参照・バイナリ取得も同経路）。

### 認証（`/web-setup` を第一候補にする）

GitHub 認証の資格情報を**平文の環境変数に長命 PAT で置かない**方針を採る（[ADR-0066](adr/0066-gh-cli-via-gh-token-on-web.md)）。環境変数は公式明記のとおり**専用の秘密ストアではなく、環境を編集できる人に平文で見える**（"A dedicated secrets store is not yet available. ... visible to anyone who can edit this environment."）。優先順位は次のとおり:

1. **第一候補: `/web-setup`（マネージド）** — ローカルの Claude Code CLI で `/web-setup` を実行し、ローカルの `gh` トークンを**Claude アカウントに紐付ける**（平文 env config には入らない）。Projects #4 操作には `project` スコープが要るので、事前にローカルで `gh auth refresh -s project` してから `/web-setup` する。
   - 手順: ローカルで `gh auth login`（未認証なら）→ `gh auth refresh -s project` → Claude Code CLI で `/login` → `/web-setup`。
2. **フォールバック: fine-grained・短命 PAT を環境変数に**（`/web-setup` でスコープが届かない場合のみ）。**対象リポジトリ 1 つ・権限は Contents / Issues / Pull requests / Projects（Read and write）のみ・短い有効期限（例 30 日）**の fine-grained PAT を発行し、`GH_TOKEN=<PAT>`（`.env` 形式・引用符なし）を環境変数に置く。gh は env の `GH_TOKEN`（無ければ `GITHUB_TOKEN`）を自動採用する（`gh auth login` 不要）。**平文で保存され編集者に見える**点を許容したうえで、最小権限＋短命＋ローテーションで被害範囲を絞る。ローカルは fnox + 1Password で解決済み（[secrets 規約](../.claude/rules/secrets.md)）なので、この平文保存は web 環境限定の割り切り。
3. **組み込み GitHub App のみ**では、git（clone/push）は資格情報プロキシが担うが、`gh` の repo/org/Projects（GraphQL）API までは届かない見込み（下表の実測）。基本的な git/PR 以上を回すには 1 か 2 が要る。

### 実測メモ（この follow-up の実装セッション）

> **重要な前提**: この実装セッションは**自動起動の管理環境**で、egress が**リポジトリスコープの egress プロキシ**越し・`GH_TOKEN` は**プロキシ注入の資格情報**（自前 PAT でも `/web-setup` トークンでもない）だった。ユーザーが対話的に開くセッションとは条件が異なるため、下表は「env と gh の素の挙動」の参考にとどめる。

| 検証 | 結果 | 意味 |
|------|------|------|
| `echo ${GH_TOKEN:+set}` | **set**（`GITHUB_TOKEN` も） | env は**非対話シェルに届く**。#611 の落とし穴3（env が setup 後に消える）は再発せず、env 橋渡し（remoteEnv 等）は不要。 |
| `gh api user -q .login` | **`ptiringo`** | gh が env の `GH_TOKEN` を採用し user レベル API は通る。 |
| `gh api rate_limit -i`（`X-Oauth-Scopes`） | `repo, project, read:org, ...` | 背後トークンは `repo` + `project` スコープを保持。 |
| `gh auth status` | 「token is invalid」と表示 | gh 独自の検証ヒューリスティックの表示に過ぎず、実 API（上記）は通る。 |
| `gh issue list` / `gh pr list` / `gh project view 4` | このセッションでは **403** | プロキシが GraphQL/repo REST を Claude GitHub App 経由にゲートしていたため（実装セッション固有）。 |

### ユーザーの対話セッションで確定すべき点（実測 TODO）

自動セッションでは `/web-setup` を実行できず、Projects 操作の 403 も実装環境固有だった。**対話的な Web セッションで次を実測し、本節を確定すること**:

- `/web-setup`（`project` スコープ付き）だけで `gh issue list` / `gh pr list` / `gh project view 4 --owner ptiringo` / PR マージが通るか。
- 通れば **1（`/web-setup`）で完結**——環境変数への PAT 登録は不要。
- Projects（GraphQL）等が届かない操作があれば、その操作に限って **2（fine-grained・短命 PAT）** で補い、本節に「どの操作が PAT を要したか」を記録する。

## スコープ外（別 Issue で扱う）

- terraform MCP（`docker run`）のクラウド動作確認（#629）。
