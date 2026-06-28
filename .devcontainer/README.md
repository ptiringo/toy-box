# Dev Container

再現可能な開発環境を [Dev Container](https://containers.dev/) で提供する。**IntelliJ IDEA を第一優先**のクライアントとし、VS Code / GitHub Codespaces からも利用できる（仕様自体はポータブル）。

## 構成

| ファイル | 役割 |
|---------|------|
| `devcontainer.json` | コンテナ定義。base image + features + エディタ設定 |
| `post-create.sh` | 作成後の一度きりセットアップ（firewall ツール導入、mise trust / install、lefthook install） |
| `init-firewall.sh` | egress を default-deny + 許可リストに制限（毎起動時に root 実行） |
| `allowed-domains.txt` | firewall の許可ドメイン（唯一の出所・コミット共有） |

- **base image**: `mcr.microsoft.com/devcontainers/base:bookworm`（Debian 系。非 root の `vscode` ユーザー同梱）
- **features**:
  - `ghcr.io/devcontainers-extra/features/mise:1` — [mise](https://mise.jdx.dev/) 本体
  - `ghcr.io/anthropics/devcontainer-features/claude-code:1.0` — Claude Code CLI（node 依存は feature が自動解決）
  - `ghcr.io/devcontainers/features/docker-in-docker:2` — Docker-in-Docker（terraform MCP の `docker run` 用）

### ツールバージョンの出所は `mise.toml` のみ

devcontainer はバージョンを二重管理しない。mise feature は **mise 本体だけ**を入れ、ツール実体（java(temurin-21) / actionlint / editorconfig-checker / fnox / gitleaks / lefthook / terraform / zizmor）は `post-create.sh` の `mise install` が `mise.toml` に従って導入する。バージョンを変えたいときは `mise.toml` だけを編集する。

### PATH への載り方

- **対話シェル**: `post-create.sh` が `~/.bashrc` に `mise activate bash` を仕込む（`JAVA_HOME` 等も注入）。
- **非対話プロセス（IntelliJ の Gradle 実行など）**: `devcontainer.json` の `remoteEnv` で mise の shims（`~/.local/share/mise/shims`）を `PATH` に追加する。
- **Claude Code セッション**: 既存の `.claude/hooks/session-start-mise.sh`（SessionStart hook）が `mise hook-env` を流し込むため、`mise exec --` プレフィックス無しで `./gradlew` 等を実行できる。

## 使い方

### IntelliJ IDEA（主シナリオ）

JetBrains Gateway もしくは IntelliJ 内蔵の Dev Containers サポートからリポジトリを開く。Gradle JVM はコンテナ内 JDK（mise が提供する temurin-21）を指すように設定する。`customizations.jetbrains.plugins` で ktfmt / detekt プラグインを宣言済み。

### VS Code / Codespaces

"Reopen in Container" で開く。`customizations.vscode.extensions` で Java / Kotlin / Gradle / EditorConfig / mise の拡張を最小限宣言している。

### 動作確認

```bash
./gradlew check     # ktfmt + detekt + test + カバレッジゲート
claude --version    # Claude Code CLI が入っていること
mise list           # mise.toml のツールが解決されていること
```

## ネットワーク egress firewall

コンテナの外向き通信（egress）を **デフォルト拒否 + 許可リスト**に制限し、Claude Code（エージェント）や
コンテナ内プロセスが任意の外部へ通信できる状態を絞る（[issue #304](https://github.com/ptiringo/toy-box/issues/304) / [ADR-0037](../docs/adr/0037-devcontainer-egress-firewall.md)）。

### 仕組み

- `init-firewall.sh` が `ipset`/`iptables` で **OUTPUT チェーンを default-deny + 許可リスト化**する。
  許可先は `allowed-domains.txt`（解決した IP）+ GitHub の IP レンジ（`api.github.com/meta` 由来）。
- iptables ルールはコンテナ再起動で消えるため、`devcontainer.json` の `postStartCommand` で
  **毎起動時に `sudo .devcontainer/init-firewall.sh`** を実行する（`--cap-add=NET_ADMIN/NET_RAW` 付き）。
- スクリプト末尾の**自己テスト**が、許可外（example.com）の遮断・許可済み（api.github.com）の疎通を検査し、
  想定外なら非ゼロ終了する。

### 許可リストの編集

通信が許可リスト漏れでブロックされたら `allowed-domains.txt` にドメインを 1 行追記し、
firewall を貼り直す:

```bash
ipset list allowed-domains | head            # 現在許可されている IP を確認
# allowed-domains.txt に必要なドメインを追記してから:
sudo .devcontainer/init-firewall.sh          # 再適用（自己テストも走る）
```

`allowed-domains.txt` が許可リストの**唯一の出所**。Claude Code の sandbox `allowedHosts`
（`.claude/settings.local.json`）とは**別レイヤー**で、そちらは非コミットの個人設定・Claude Code 自身の
コマンド実行制御に効く。両者は性質が非対称なため一本化せず併存させる。

### Docker-in-Docker との関係（known-caveat）

firewall は **dev コンテナ自身の egress（OUTPUT）に集中**し、`FORWARD` チェーンは管理しない。これにより
DinD（terraform MCP の `docker run`）を壊さないが、**DinD 子コンテナ経由の egress は厳密には絞られない**。
Claude Code 本体は子コンテナにいないため主目的（エージェント egress の制限）は守られる。DinD で外部イメージを
pull する場合は `allowed-domains.txt` 末尾の Docker Hub 雛形を有効化する。

### IPv6 は対象外（known-caveat）

本 firewall は **IPv4（`iptables`）のみ**を制御し、`ip6tables`（IPv6 の egress）は対象外。コンテナに IPv6
接続性がある場合、IPv6 経由の egress は許可リストで絞られない。実害可能性は低い（Docker は既定で IPv6 を無効化、
かつ許可外ホストに IPv6 で到達できると `init-firewall.sh` 末尾の自己テストが失敗して検知する）が、ギャップとして
明示する。IPv6 の完全対応（`ip6tables` の default-deny + IPv6 allowlist）は別 issue で扱う。

## 既知の制約（スコープ外）

本 devcontainer は「Java/Gradle 開発 + Claude Code」の土台を優先しており、以下は別途検討する（[issue #302](https://github.com/ptiringo/toy-box/issues/302) のスコープ外）。

- ~~**ネットワーク firewall サンドボックス**~~ → #304 / ADR-0037 で導入済み（上記「ネットワーク egress firewall」参照）。
- **シークレット（fnox + 1Password）**: `op` のデスクトップアプリ連携はコンテナから到達できない。現状 `fnox.toml` の `[secrets]` は空のため当面の支障はない。
