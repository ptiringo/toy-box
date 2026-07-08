#!/bin/bash
# PostToolUse hook: 編集ファイルが dprint 対象の設定ファイル(TOML/JSON/JSONC/YAML)であれば
# `dprint check` でフォーマット違反を検査する。
#
# - 違反があれば exit 2 を返し、Claude にフィードバックして自動整形を促す。
# - 対象外ファイル / dprint 未インストールの場合は exit 0 で黙って抜ける。

set -uo pipefail

if ! command -v jq >/dev/null 2>&1; then
    exit 0
fi
payload="$(cat)"

file_path="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_response.filePath // empty')"
[ -z "$file_path" ] && exit 0
[ -f "$file_path" ] || exit 0

# macOS の /tmp -> /private/tmp などシンボリックリンク差異を吸収するため
# 両方を realpath で正規化してから prefix を取り除く。
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
abs_file="$(/usr/bin/env python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$file_path" 2>/dev/null || echo "$file_path")"
abs_root="$(/usr/bin/env python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$repo_root" 2>/dev/null || echo "$repo_root")"
rel_path="${abs_file#"$abs_root"/}"

# 対象拡張子のみ(TOML/JSON/JSONC/YAML)。
case "$rel_path" in
    *.toml | *.json | *.jsonc | *.yml | *.yaml) ;;
    *) exit 0 ;;
esac

# dprint.json の excludes と整合する除外(生成物・個人設定)。
case "$rel_path" in
    build/* | */build/* | .gradle/* | */.gradle/* | .claude/worktrees/*) exit 0 ;;
    .devcontainer/devcontainer-lock.json | .claude/settings.local.json | mise.lock) exit 0 ;;
esac

if ! command -v dprint >/dev/null 2>&1; then
    exit 0
fi

output="$(cd "$repo_root" && dprint check "$rel_path" 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    # メッセージ中の `dprint fmt ...` はユーザー向けの案内テキスト(リテラル表示が意図)。
    # shellcheck disable=SC2016
    printf 'dprint フォーマット違反が検出されました:\n%s\n`dprint fmt %s` で整形してください。\n' "$output" "$rel_path" >&2
    exit 2
fi
exit 0
