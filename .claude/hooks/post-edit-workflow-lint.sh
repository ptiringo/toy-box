#!/bin/bash
# PostToolUse hook: 編集ファイルが .github/workflows/ 配下のワークフロー定義であれば
# actionlint と zizmor で lint / セキュリティ監査を実行する。
#
# - 違反があれば exit 2 を返し、Claude にフィードバックして修正を促す。
# - 対象外ファイル / ツール未インストールの場合は exit 0 で黙って抜ける。

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

# .github/workflows/ 配下の YAML のみ対象
case "$rel_path" in
    .github/workflows/*.yml) ;;
    .github/workflows/*.yaml) ;;
    *) exit 0 ;;
esac

# PATH 直接 / mise exec 経由のどちらかでツールを実行する。
# どちらでも見つからない場合は exit 127 を返し、呼び出し側でスキップ判定する。
run_tool() {
    if command -v "$1" >/dev/null 2>&1; then
        "$@"
    elif command -v mise >/dev/null 2>&1; then
        mise exec -- "$@"
    else
        return 127
    fi
}

failed=0
messages=""

output="$(cd "$repo_root" && run_tool actionlint "$rel_path" 2>&1)"
status=$?
if [ "$status" -ne 0 ] && [ "$status" -ne 127 ]; then
    failed=1
    messages="${messages}actionlint の指摘:
${output}

"
fi

output="$(cd "$repo_root" && run_tool zizmor --offline --quiet "$rel_path" 2>&1)"
status=$?
if [ "$status" -ne 0 ] && [ "$status" -ne 127 ]; then
    failed=1
    messages="${messages}zizmor の指摘:
${output}

"
fi

if [ "$failed" -ne 0 ]; then
    printf 'GitHub Actions ワークフローに問題が検出されました。修正してください。\n\n%s' "$messages" >&2
    exit 2
fi
exit 0
