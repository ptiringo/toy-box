#!/bin/bash
# Stop hook: 直近の未コミット変更に infra/*.tf が含まれているとき、Terraform の
# 構文/参照検査（terraform validate）と IaC セキュリティ misconfig スキャン（trivy config）
# を実行する。
#
# - HEAD との差分で .tf 変更が無いターンは何もしない。
# - 違反があれば exit 2 を返し、Claude にフィードバックする。
# - terraform / trivy が未インストールなら、当該チェックのみ黙ってスキップする。

set -uo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$repo_root" || exit 0

# HEAD との差分に infra/*.tf が含まれない場合はスキップ
if ! git diff HEAD --name-only 2>/dev/null | grep -qE '^infra/.*\.tf$'; then
    exit 0
fi

# terraform validate（terraform 未インストール時はスキップ）
if command -v terraform >/dev/null 2>&1; then
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
fi

# Trivy による IaC セキュリティ misconfig スキャン（ADR-0017 / #378。mise 未導入時はスキップ）。
# mise 管理ツールを確実に解決するため mise exec 経由で呼ぶ（lefthook / CI と同型。
# trivy 追加前に開始したセッションでは bare PATH に乗らないため command -v trivy には依存しない）。
# --exit-code 1 で findings があれば非ゼロ終了し、lefthook / CI と同じ hard gate を Claude にも適用する。
if command -v mise >/dev/null 2>&1; then
    trivy_output="$(cd infra && mise exec -- trivy config . --exit-code 1 --quiet 2>&1)"
    status=$?
    if [ "$status" -ne 0 ]; then
        printf 'Trivy が IaC misconfig を検出しました:\n%s\n' "$trivy_output" >&2
        exit 2
    fi
fi

exit 0
