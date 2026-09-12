#!/usr/bin/env bash
# `mise install` が走った後に mise.lock が書き換わっていないかを見る（#880）。
#
# `mise install --locked` は「lock どおりのバージョンを入れる」ための呼び出しだが、mise 自身が
# その過程で lock を書き直すことがある（#762 が扱うローカルの症状）。runner 上で同じことが起きると
# 「lock どおりに入った」という CI の前提が黙って崩れる。CI はコミットしないので、汚れた lock は
# ジョブ終了とともに捨てられ、誰の目にも触れない。
#
# 実際に #878 で一度だけ観測された（run 33862634725 で `mise.lock | 1 +`）。ただし当時の再現は
# できていない: 当時のコミット（967907e）・当時の mise 2026.9.1・当時の mise-action（v4.2.5）・
# MISE_ENV=ci・同じ `mise install --locked editorconfig-checker` まで揃えて runner 上で走らせても
# 再発しなかった（#880 で 4 回実測）。unstaged の diff と `git add --renormalize .` 後の
# `git diff --cached` の両方を見ても差分ゼロ。原因は runner image か、mise が参照する外部状態
# （aqua-registry / GitHub API のその時点のレスポンス）に帰着し、固定できていない。
#
# したがってこの検査は**落とさない**。原因が上流側にあるため、赤にすると自分では直せない理由で
# CI が止まる側に倒れる。目的は「起きたときに気づける」ことなので warning に留める。
#
# 検出しないもの（既知の穴）:
#   - mise.lock 以外のファイルの書き換え。mise が .tool-versions 等を触った場合は見ない
#   - 書き換えの正しさ。lock が「どう」変わったかは diff を出すだけで、良し悪しは判定しない
#   - install より前から作業ツリーが汚れていた場合の切り分け。CI は checkout 直後が clean なので
#     runner 上では成り立つが、ローカルで使うときは自分で clean を確かめてから呼ぶこと
#
# 使い方:
#   scripts/check-mise-lock-drift.sh
#   - 引数は取らない。リポジトリ root からの実行を前提とする。
#   - CI では `mise install` を走らせるステップより**後**に置く（前に置くと何も見ない）。
#   - 手元で使うなら、clean な状態から `mise install` を流した直後に叩く。
# 終了コード: 常に 0（warning に留める。上のコメントの理由）。
set -euo pipefail

LOCK="mise.lock"

if [ ! -f "$LOCK" ]; then
  echo "NG: $LOCK がありません（リポジトリ root から実行してください）。" >&2
  exit 1
fi

if git diff --quiet -- "$LOCK"; then
  echo "$LOCK は書き換わっていない"
  exit 0
fi

MESSAGE="mise install が $LOCK を書き換えました。「lock どおりのバージョンが入った」という前提が崩れています（#880）"

# GitHub Actions ではアノテーションとして出す（ジョブのサマリと該当ファイルに紐づく）。
if [ -n "${GITHUB_ACTIONS:-}" ]; then
  echo "::warning file=$LOCK::$MESSAGE"
else
  echo "WARN: $MESSAGE" >&2
fi

git --no-pager diff --stat -- "$LOCK"
git --no-pager diff -- "$LOCK"
