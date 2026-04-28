#!/bin/bash
# SessionStart hook: mise が管理するツールを Claude Code のシェルセッションに読み込む。
#
# mise (https://mise.jdx.dev/) がプロジェクトごとに指定するツール（mise.toml 参照）を
# Claude Code が起動するシェルでも利用できるようにするため、`mise hook-env` の出力を
# $CLAUDE_ENV_FILE へ追記する。これにより `mise exec --` プレフィックス無しで
# プロジェクト指定バージョンの terraform 等を直接実行できる。
#
# mise が未インストールの環境では何もせず終了する（Claude Code の起動を妨げないため）。
# $CLAUDE_ENV_FILE が未設定の場合（hook が Claude Code 以外の文脈で実行された等）も
# 空文字へのリダイレクトで `ambiguous redirect` エラーになるのを避けるため何もせず終了する。

if command -v mise >/dev/null 2>&1 && [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  mise hook-env -s bash >> "$CLAUDE_ENV_FILE"
fi
