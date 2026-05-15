#!/bin/bash
# Stop hook: 直近の未コミット変更に Kotlin / Java ソースが含まれているとき、
# `./gradlew test --daemon` を実行してテストの破壊を検知する。
#
# - HEAD との差分で *.kt / *.kts / *.java の変更が無いターンはスキップする。
# - 失敗時は exit 2 で Claude にフィードバックして修正を促す。

set -uo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$repo_root" || exit 0

if ! git diff HEAD --name-only 2>/dev/null | grep -qE '\.(kt|kts|java)$'; then
    exit 0
fi

if [ ! -x ./gradlew ]; then
    exit 0
fi

output="$(./gradlew test --daemon --console=plain 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf './gradlew test に失敗しました:\n%s\n' "$output" >&2
    exit 2
fi
exit 0
