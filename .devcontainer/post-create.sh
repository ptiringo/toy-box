#!/usr/bin/env bash
# Dev Container 作成後に一度だけ実行されるセットアップ。
#
# 役割:
#   1. 対話シェルで mise を有効化（PATH / JAVA_HOME 等を注入）
#   2. mise.toml に従って全ツールを導入（バージョンの出所は mise.toml のみ）
#   3. lefthook で git フックを設定
#
# mise feature は mise バイナリを入れるだけで activate / `mise install` は行わないため、
# それらを本スクリプトで担う。
set -euo pipefail

# egress firewall（init-firewall.sh）が依存するツールを導入する。
# base image（bookworm）はこれらを含まないため apt で入れる。postCreate で入れたパッケージは
# コンテナ FS に永続化し、再起動をまたいで残る（フル再構築時のみ再導入される）。
echo "installing firewall toolchain (iptables / ipset / dnsutils / jq) ..."
sudo apt-get update
sudo apt-get install -y --no-install-recommends iptables ipset dnsutils jq

# 対話 bash 起動時に mise を有効化する（重複追記を避ける）。
if ! grep -qs 'mise activate bash' "${HOME}/.bashrc"; then
  # `$(mise activate bash)` は .bashrc にリテラルとして書き込むのが意図（ここで展開しない）。
  # shellcheck disable=SC2016
  echo 'eval "$(mise activate bash)"' >> "${HOME}/.bashrc"
fi

# このリポジトリの mise.toml を信頼してからツールを導入する。
# （信頼しないと mise が設定を読まずツールが入らない）
mise trust
mise install

# git フック（pre-commit / pre-push / commit-msg）をインストールする。
# lefthook は mise 管理ツールのため mise exec 経由で呼ぶ。
# git worktree 上のコンテナなど .git が解決できない環境では失敗しうるが、
# それでコンテナ作成全体を止めないよう警告にとどめる（後から手動で再実行できる）。
if ! mise exec -- lefthook install; then
  echo "warn: lefthook install に失敗しました。コンテナ内で git が利用可能になったら 'mise exec -- lefthook install' を再実行してください。" >&2
fi
