# Claude Code on the web セットアップ手順

[Claude Code on the web](https://code.claude.com/docs/ja/claude-code-on-the-web) は Anthropic 管理のクラウド VM でセッションを実行する。本リポジトリをクラウド／モバイルから回すための設定手順を記録する。

> **これは Dev Container（`.devcontainer/`）とは別の実行環境**である。web 環境は containers.dev の devcontainer ではなく Anthropic 管理 VM で、`devcontainer.json` / features / firewall は使わない。共通するのは「ツールの出所は `mise.toml`」という一点のみ。

## ゴール

クラウドセッションで `./gradlew check`（ビルド + テスト + Testcontainers + カバレッジゲート）が緑になること。kotlin-lsp / gh + Projects 運用 / terraform MCP のフル同等化はスコープ外（本ドキュメント末尾「スコープ外」参照）。

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

`scripts/web-setup.sh` は mise を導入し、`mise install java`（JDK 25 のみ）を実行して toolchain を検証する。全ツールは入れない（`./gradlew check` に不要なため）。検証の `mise exec` は **java にスコープする**（`mise exec java -- ...`）。ツール未指定の `mise exec -- ...` は mise.toml の全ツールを auto-install してしまい、スコープ外の kotlin-lsp（JetBrains ホストは Custom 未許可）や GitHub API のレート制限（未認証 60/h）で落ちるため（実測）。

> **なぜ `bash scripts/web-setup.sh` ではなく `find` で絶対パス解決するか**: セットアップスクリプトの実行時 CWD は公式ドキュメントに記載がなく、リポジトリルートである保証がない。repo 相対パスで呼ぶと `exit 127: No such file or directory` になる（実測）。リポジトリのクローン自体は setup 実行時点で存在する（"Cloud sessions start from a fresh clone ... Everything committed is available"）が、クローン先パスも未文書化のため、コミット済みヘルパーを `find` で引いて絶対パスで実行する。ヘルパー側は自分の位置からリポジトリルートへ `cd` するので、以降の `mise trust` / `mise install`（`mise.toml` を読む）は正しく走る。

> **なぜ SessionStart フックでなく setup スクリプトか**: 公式は「ランタイム/CLI の導入は setup スクリプト、両環境で要るプロジェクト設定は SessionStart フック」を推奨している。mise + JDK の導入は前者にあたる。加えて SessionStart フックに寄せると、初回セッションで mise 未導入のまま既存の `session-start-mise.sh`（`mise hook-env`）が空振りし PATH が注入されない順序問題が起きる。setup スクリプトは Claude Code 起動前に完走するのでこれを避けられる。

### 2. Custom 許可ドメイン

クラウド環境のデフォルト Trusted には Maven Central・`plugins.gradle.org`・`services.gradle.org`・`spring.io`・`repo.spring.io`・`kotlinlang.org`・Docker Hub（`registry-1.docker.io` / `auth.docker.io` / `production.cloudfront.docker.com`）が含まれる。したがって Gradle 依存解決と Testcontainers の image pull は追加設定なしで通る見込み。

デフォルト Trusted に**無く、Custom に足す必要がある**のは mise 本体・ツール取得系（実クラウドで確認済み）:

- `mise.run` … mise インストールスクリプトの配信元（`curl https://mise.run | sh`）
- `mise.jdx.dev` … mise バイナリの取得先（GitHub が使えない場合のフォールバック）
- `mise-versions.jdx.dev` … `mise install` のバージョン解決
- `mise-java.jdx.dev` … `mise install java` が JVM メタデータ（tar の所在）を引く先

GitHub（`github.com` / `objects.githubusercontent.com`）は default Trusted なので追加不要（mise バイナリ・チェックサム・JDK tar 実体はそこから来る）。`api.adoptium.net` は不要だった（mise は `mise-java.jdx.dev` 経由で解決する）。

> これらは `.devcontainer/allowed-domains.txt` には**無い web 固有の要求**である。devcontainer は mise を feature で導入するため `mise.run`／`mise-java.jdx.dev` を使わないが、web は素の VM に `curl mise.run` でブートストラップするため追加で要る。Docker Hub 等の重複する既定 Trusted 分は上記のとおり `.devcontainer/allowed-domains.txt` を出所として二重管理しない。`./gradlew check` を実際に緑にする過程で更に不足ホストが出たら追記する。

> **クローン先**: 実測でリポジトリは `/home/user/toy-box` に置かれた（`mise trusted /home/user/toy-box`）。ただしこのパスは公式未文書化なので UI では `find` による絶対パス解決（上記「1. セットアップスクリプト」）に依存する。

### 3. 環境変数

現状、`./gradlew check` に必要な環境変数は無い。将来 GH_TOKEN 等が要るようになったらここに追記する。

## 検証（初回セッションで実施）

初回のクラウドセッションで次を確認し、結果を本ドキュメントに反映する:

1. セッション開始後、`./gradlew check` が緑になること。
   - PATH は既存の `.claude/hooks/session-start-mise.sh`（SessionStart フック）が `mise hook-env` で注入するので、`mise exec --` プレフィックス無しで `./gradlew` が動く。
2. 16 GB RAM で Testcontainers Postgres（`postgres:17-alpine` + Ryuk）が起動し JDBC 契約テストが通ること。
3. ドメイン不足で pull / 依存解決が落ちたら、落ちたホストを Custom 許可ドメインに足して再実行する。確定したら上記「2. Custom 許可ドメイン」を実測値に更新する。

## スコープ外（別 Issue で扱う）

- kotlin-lsp のクラウド有効化（JetBrains ホスト・編集時診断は補助操舵で check ゲートではない）。
- gh + `GH_TOKEN` による Projects・PR マージ運用。
- terraform MCP（`docker run`）のクラウド動作確認。
