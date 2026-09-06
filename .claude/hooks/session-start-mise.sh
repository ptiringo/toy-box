#!/bin/bash
# SessionStart hook: mise が管理するツールを Claude Code のシェルセッションに読み込む。
#
# mise (https://mise.jdx.dev/) がプロジェクトごとに指定するツール（mise.toml 参照）を
# Claude Code が起動するシェルでも利用できるようにするため、`mise hook-env` の出力を
# $CLAUDE_ENV_FILE へ追記する。これにより `mise exec --` プレフィックス無しで
# プロジェクト指定バージョンの terraform 等を直接実行できる。
#
# `mise trust` を先に実行している理由:
# mise の trust はパス単位で、未 trust の mise.toml があると mise は設定の読み込みを
# エラーで打ち切り、[tools] を一切適用しない（PATH にツールが入らず `command not found`
# になる）。クローン直後のリポジトリは untrusted なので、最初のセッションでそれを
# trust する役目をここが負っている。
#
# git worktree は、trust 済みリポジトリの一部として自動で trusted になる（mise 2026.8.12
# で実測）。この行は worktree のためではないので、「worktree 用の行」と読んで消さないこと。
# trust は冪等で、既に trust 済みの環境で再実行しても副作用はない。
#
# 安全のため、無引数の `mise trust`（カレントディレクトリ配下を一括信頼）ではなく、
# `git rev-parse --show-toplevel` で取得した本リポジトリ（または worktree）の
# ルート直下に存在する mise.toml に対してのみ trust を行う。これにより
# 意図しないディレクトリの mise 設定が信頼されることを防ぐ。

if command -v mise >/dev/null 2>&1 && [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  if repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" && [ -f "$repo_root/mise.toml" ]; then
    mise trust --quiet "$repo_root/mise.toml" 2>/dev/null || true
  fi
  mise hook-env -s bash >> "$CLAUDE_ENV_FILE"
fi
