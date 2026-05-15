#!/bin/bash
# PostToolUse hook: 編集ファイルが infra/ 配下の Terraform ソースであれば
# `terraform fmt -check` でフォーマット違反を検査する。
#
# - 違反があれば exit 2 を返し、Claude にフィードバックして自動整形を促す。
# - 対象外ファイル / terraform 未インストールの場合は exit 0 で黙って抜ける。

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

# infra/ 配下の *.tf のみ対象（モジュール配下も含む）
case "$rel_path" in
    infra/*.tf) ;;
    infra/*/*.tf) ;;
    infra/*/*/*.tf) ;;
    *) exit 0 ;;
esac

if ! command -v terraform >/dev/null 2>&1; then
    exit 0
fi

output="$(cd "$repo_root" && terraform fmt -check -recursive infra/ 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf 'terraform fmt 違反が検出されました:\n%s\n`terraform fmt -recursive infra/` で整形してください。\n' "$output" >&2
    exit 2
fi
exit 0
