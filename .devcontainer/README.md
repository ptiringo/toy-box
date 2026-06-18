# Dev Container

再現可能な開発環境を [Dev Container](https://containers.dev/) で提供する。**IntelliJ IDEA を第一優先**のクライアントとし、VS Code / GitHub Codespaces からも利用できる（仕様自体はポータブル）。

## 構成

| ファイル | 役割 |
|---------|------|
| `devcontainer.json` | コンテナ定義。base image + features + エディタ設定 |
| `post-create.sh` | 作成後の一度きりセットアップ（mise trust / install、lefthook install） |

- **base image**: `mcr.microsoft.com/devcontainers/base:bookworm`（Debian 系。非 root の `vscode` ユーザー同梱）
- **features**:
  - `ghcr.io/devcontainers-extra/features/mise:1` — [mise](https://mise.jdx.dev/) 本体
  - `ghcr.io/anthropics/devcontainer-features/claude-code:1.0` — Claude Code CLI（node 依存は feature が自動解決）

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

## 既知の制約（スコープ外）

本 devcontainer は「Java/Gradle 開発 + Claude Code」の土台を優先しており、以下は別途検討する（[issue #302](https://github.com/ptiringo/toy-box/issues/302) のスコープ外）。

- **terraform MCP サーバーの Docker 依存**: `.mcp.json` の `terraform` サーバーは `docker run hashicorp/terraform-mcp-server` を前提とするため、コンテナ内から使うには Docker-in-Docker / Docker-outside-of-Docker が必要。`context7`（http）はコンテナ内でもそのまま使える。
- **ネットワーク firewall サンドボックス**（`init-firewall.sh` 相当）は導入しない。
- **シークレット（fnox + 1Password）**: `op` のデスクトップアプリ連携はコンテナから到達できない。現状 `fnox.toml` の `[secrets]` は空のため当面の支障はない。
