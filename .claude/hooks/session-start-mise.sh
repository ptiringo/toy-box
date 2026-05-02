#!/bin/bash
# SessionStart hook: mise が管理するツールを Claude Code のシェルセッションに読み込む。
#
# mise (https://mise.jdx.dev/) がプロジェクトごとに指定するツール（mise.toml 参照）を
# Claude Code が起動するシェルでも利用できるようにするため、`mise hook-env` の出力を
# $CLAUDE_ENV_FILE へ追記する。これにより `mise exec --` プレフィックス無しで
# プロジェクト指定バージョンの terraform 等を直接実行できる。
#
# `mise trust` を先に実行している理由:
# git worktree でセッションが起動するとパスが毎回ユニークになり、メイン repo 側で
# trust 済みでも worktree のパスは untrusted 扱いになる。untrusted な状態だと
# `mise hook-env` は警告を出した上で env 設定を行わないため、PATH にツールが
# 入らず `command not found` になってしまう。これを回避するため毎回 trust する。
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
