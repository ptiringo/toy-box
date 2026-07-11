#!/usr/bin/env bash
# Claude Code on the web のクラウドセッション用セットアップ。
#
# クラウド環境 UI の「セットアップスクリプト」欄から呼ぶ本体。呼び出し方は
# docs/claude-code-on-the-web.md 参照（クローン位置は setup 実行時の CWD からは不定なので
# 絶対パスで呼ぶ）。クラウド VM は素の JDK 21 だが本リポジトリの Gradle toolchain は 25 を
# 要求するため、mise で Temurin 25 を供給してギャップを埋める。PATH 注入は既存の
# .claude/hooks/session-start-mise.sh（SessionStart フック）が毎セッション担うので、
# ここでは「mise 導入 → mise install java → mise activate 仕込み」までを一度だけ行う。
#
# スコープは `./gradlew check` を緑にすることに限定し、`mise install`（全ツール）ではなく
# `mise install java`（JDK 25 のみ）だけを入れる。kotlin-lsp（JetBrains ホスト・スコープ外）や
# pipx 系ツールの取得失敗で全体を止めない・不要な Custom 許可ドメインを増やさないため。
#
# 冪等: 再実行しても mise 再導入や .bashrc への重複追記は行わない。
set -euo pipefail

# 呼び出し時の CWD に依存せずリポジトリルートで動くよう、スクリプト自身の位置から移動する。
# Claude Code on the web の setup スクリプトは CWD がリポジトリルートである保証がなく（公式未記載）、
# repo 相対パスで呼ぶと 127（No such file or directory）になる。後続の `mise trust` /
# `mise install`（mise.toml を読む）はリポジトリルートで実行する必要がある。
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# mise 本体が無ければ導入し、このスクリプト内でも使えるよう PATH に載せる。
if ! command -v mise >/dev/null 2>&1; then
  echo "installing mise ..."
  curl -fsSL https://mise.run | sh
fi
export PATH="${HOME}/.local/bin:${PATH}"

if ! command -v mise >/dev/null 2>&1; then
  echo "error: mise の導入に失敗しました（PATH に mise が見つかりません）。" >&2
  exit 1
fi

# 対話 bash 起動時に mise を有効化する（重複追記を避ける。post-create.sh と同方式）。
if ! grep -qs 'mise activate bash' "${HOME}/.bashrc"; then
  # `$(mise activate bash)` は .bashrc にリテラルとして書き込むのが意図（ここで展開しない）。
  # shellcheck disable=SC2016
  echo 'eval "$(mise activate bash)"' >> "${HOME}/.bashrc"
fi

# このリポジトリの mise.toml を信頼してから JDK 25 のみ導入する。
mise trust
echo "installing JDK 25 via mise (java only) ..."
mise install java

# 検証: JDK 25 が解決でき、Gradle が toolchain を満たせること。
echo "verifying toolchain ..."
mise exec -- java -version
mise exec -- ./gradlew --version

echo "web-setup done. Run './gradlew check' to validate the session."
