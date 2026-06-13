#!/bin/bash
# Stop hook: 直近の未コミット変更に Kotlin ソースが含まれているとき、
# `./gradlew ktfmtFormat` でフォーマットを適用し、`./gradlew detekt` で静的解析を行う。
#
# - HEAD との差分で *.kt / *.kts の変更が無いターンはスキップする。
# - ./gradlew が存在しない、または実行可能でない場合もスキップする。
# - 失敗時は exit 2 で Claude にフィードバックして修正を促す。

set -uo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$repo_root" || exit 0

if ! git diff HEAD --name-only 2>/dev/null | grep -qE '\.(kt|kts)$'; then
    exit 0
fi

if [ ! -x ./gradlew ]; then
    exit 0
fi

output="$(./gradlew ktfmtFormat --daemon --console=plain 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf './gradlew ktfmtFormat に失敗しました:\n%s\n' "$output" >&2
    exit 2
fi

output="$(./gradlew detekt --daemon --console=plain 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf './gradlew detekt に失敗しました:\n%s\n' "$output" >&2
    exit 2
fi
exit 0
