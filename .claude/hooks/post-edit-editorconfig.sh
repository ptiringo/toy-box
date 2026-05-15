#!/bin/bash
# PostToolUse hook: 編集ファイルに対して EditorConfig 違反を検査する。
#
# Claude Code から渡される stdin JSON の tool_input.file_path / tool_response.filePath
# を読み取り、対象ファイルが実在する場合のみ ec (editorconfig-checker) を実行する。
#
# - 違反があれば exit 2 を返し、stderr を Claude へフィードバックする。
# - file_path が空 / ファイル未存在 / ec 未インストールの場合は黙って exit 0 で抜ける。

set -uo pipefail

# stdin の JSON は 1 度しか読めないので変数化する。jq が無い環境ではスキップ。
if ! command -v jq >/dev/null 2>&1; then
    exit 0
fi
payload="$(cat)"

file_path="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_response.filePath // empty')"
[ -z "$file_path" ] && exit 0
[ -f "$file_path" ] || exit 0

# ec はリポジトリ直下の .editorconfig を起点にチェックするため repo root に移動する。
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$repo_root" || exit 0
# macOS の /tmp -> /private/tmp などシンボリックリンク差異を吸収する。
abs_file="$(/usr/bin/env python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$file_path" 2>/dev/null || echo "$file_path")"
abs_root="$(/usr/bin/env python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$repo_root" 2>/dev/null || echo "$repo_root")"
rel_path="${abs_file#"$abs_root"/}"

if ! command -v ec >/dev/null 2>&1; then
    exit 0
fi

output="$(ec "$rel_path" 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf 'EditorConfig 違反が検出されました (%s):\n%s\n' "$rel_path" "$output" >&2
    exit 2
fi
exit 0
