#!/bin/bash
# Stop hook: 直近の未コミット変更に infra/*.tf が含まれているとき、
# `terraform validate` で構文エラー / 参照エラー / リソース定義不整合を検査する。
#
# - HEAD との差分で .tf 変更が無いターンは何もしない。
# - 違反があれば exit 2 を返し、Claude にフィードバックする。
# - terraform 未インストール時は exit 0 で黙って抜ける。

set -uo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$repo_root" || exit 0

# HEAD との差分に infra/*.tf が含まれない場合はスキップ
if ! git diff HEAD --name-only 2>/dev/null | grep -qE '^infra/.*\.tf$'; then
    exit 0
fi

if ! command -v terraform >/dev/null 2>&1; then
    exit 0
fi

# init が走っていない場合のみ backend 無効で init する（terraform validate の前提）
if [ ! -d infra/.terraform ]; then
    if ! init_output="$(cd infra && terraform init -backend=false -no-color 2>&1)"; then
        printf 'terraform init に失敗しました:\n%s\n' "$init_output" >&2
        exit 2
    fi
fi

output="$(cd infra && terraform validate -no-color 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf 'terraform validate に失敗しました:\n%s\n' "$output" >&2
    exit 2
fi
exit 0
