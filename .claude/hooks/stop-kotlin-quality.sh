#!/bin/bash
# Stop hook: 直近の未コミット変更に Kotlin / Java ソースが含まれているとき、
# `./gradlew check` で品質チェック一式（ktfmtCheck / detekt / test 等）を実行する。
#
# - HEAD との差分で *.kt / *.kts / *.java の変更が無いターンはスキップする。
# - ./gradlew が存在しない、または実行可能でない場合もスキップする。
# - 1 回の Gradle 起動にまとめることで、並列起動によるロック競合を避ける。
# - check はフォーマットの自動修正を行わない。ktfmtCheck の失敗は exit 2 の
#   フィードバックを受けた Claude が ktfmtFormat を実行して解消する想定。
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

output="$(./gradlew check --daemon --console=plain 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf './gradlew check に失敗しました:\n%s\n' "$output" >&2
    exit 2
fi
exit 0
