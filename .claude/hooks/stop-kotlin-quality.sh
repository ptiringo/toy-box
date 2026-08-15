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
# - 空回り・並行競合対策（#519 / #780）:
#   - Gradle クライアント JVM が走っている間はスキップする。判定は wrapper 起動が
#     付ける `-Dorg.gradle.appname=gradlew` で行う（クライアントは `-jar` 起動で
#     メインクラス名がコマンドラインに出ないため、GradleWrapperMain では当たらない）。
#     常駐 daemon はこの引数を持たないので一致しない。共有 build cache の競合を
#     避けるためマシン全体で見る。
#   - 前回失敗時から作業ツリーが変わっていなければ再実行しない（失敗指紋 dedup）。

set -uo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$script_dir/lib/stop-hook-dedup.sh"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$repo_root" || exit 0

if ! git diff HEAD --name-only 2>/dev/null | grep -qE '\.(kt|kts|java)$'; then
    exit 0
fi

if [ ! -x ./gradlew ]; then
    exit 0
fi

if command -v pgrep >/dev/null 2>&1 \
    && pgrep -f -- '-Dorg\.gradle\.appname=gradlew' >/dev/null 2>&1; then
    echo 'gradle 実行中のため check をスキップしました（並行実行ガード・#519 / #780）' >&2
    exit 0
fi

hook_name="kotlin-quality"
fingerprint="$(compute_tree_fingerprint)"

if should_skip_unchanged "$hook_name" "$fingerprint"; then
    printf '前回失敗時から作業ツリーに変化が無いため check をスキップしました（#519）。ツリーを変更するか %s を削除すると再実行されます。\n' \
        "$(dedup_state_file "$hook_name")" >&2
    exit 0
fi

output="$(./gradlew check --daemon --console=plain 2>&1)"
status=$?
if [ "$status" -ne 0 ]; then
    record_failure_fingerprint "$hook_name" "$fingerprint"
    printf './gradlew check に失敗しました:\n%s\n' "$output" >&2
    exit 2
fi
clear_failure_fingerprint "$hook_name"
exit 0
