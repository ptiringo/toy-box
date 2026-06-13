#!/bin/bash
# Stop hook: 直近の未コミット変更に Kotlin / Java ソースが含まれているとき、
# `./gradlew kotlinQuality` で ktfmtFormat → detekt → test を一括実行する。
#
# - HEAD との差分で *.kt / *.kts / *.java の変更が無いターンはスキップする。
# - ./gradlew が存在しない、または実行可能でない場合もスキップする。
# - 1 回の Gradle 起動にまとめることで実行順序を保証し、並列起動によるロック競合を避ける。
#   Java のみの変更時は ktfmtFormat / detekt が Gradle の UP-TO-DATE 判定で自動スキップされる。
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

output="$(./gradlew kotlinQuality --daemon --console=plain 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    printf './gradlew kotlinQuality に失敗しました:\n%s\n' "$output" >&2
    exit 2
fi
exit 0
