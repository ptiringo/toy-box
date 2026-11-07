#!/usr/bin/env bash
# デプロイ（Deploy API）の結果を GitHub Issue に残す（#1019 / ADR-0089）。
#
# 2026-10-23〜24 に main のデプロイ失敗へ約 19 時間気づかなかった。アプリは旧版のまま動いていたので、
# uptime check では検知できない。失敗を Issue として立て、自分に割り当てて通知を飛ばす。
#
# 振る舞い（結論ごと）:
#   - failure / timed_out / startup_failure: ラベル deploy-failure の Issue が開いていれば、その
#     Issue に run を追記する。無ければ新しく立てる。失敗が続いても Issue は 1 つに留まる
#   - success: 開いている Issue があれば、復旧した run を追記する。閉じるのは人間が原因を確かめてから
#     （自動で閉じると、気づく前に閉じてしまう）
#   - それ以外（cancelled 等）: 何もしない。人間が止めたものは失敗として扱わない
#
# Project #4 への追加はこのスクリプトでは行わない。GITHUB_TOKEN は Project を操作できないので、
# Project 側の組み込みの自動追加（label:deploy-failure）に任せる。
#
# 使い方（環境変数で受け取る。ワークフローの式をシェルへ直接展開しないため）:
#   CONCLUSION   run の結論（workflow_run.conclusion）
#   RUN_URL      失敗した run の URL
#   RUN_ATTEMPT  再実行の回数（1 始まり）
#   HEAD_SHA     デプロイ対象のコミット
#   HEAD_BRANCH  run のブランチ
#   EVENT        run を起動したイベント（push / workflow_dispatch）
#   ASSIGNEE     Issue を割り当てるユーザー
#   gh の認証と対象リポジトリは GH_TOKEN / GH_REPO で渡す。
# 終了コード: gh の操作に失敗したら非 0。
#
# 互換: macOS 標準の bash 3.2 で動くよう、非 ASCII 文字に接する変数は ${name} で書く（#864）。
set -euo pipefail

LABEL="deploy-failure"

: "${CONCLUSION:?CONCLUSION が要ります}"
: "${RUN_URL:?RUN_URL が要ります}"
: "${RUN_ATTEMPT:?RUN_ATTEMPT が要ります}"
: "${HEAD_SHA:?HEAD_SHA が要ります}"
: "${HEAD_BRANCH:?HEAD_BRANCH が要ります}"
: "${EVENT:?EVENT が要ります}"
: "${ASSIGNEE:?ASSIGNEE が要ります}"

case "${CONCLUSION}" in
  failure | timed_out | startup_failure) kind="failure" ;;
  success) kind="success" ;;
  *)
    echo "結論が ${CONCLUSION} なので何もしません。"
    exit 0
    ;;
esac

open_issue=$(gh issue list --label "${LABEL}" --state open --limit 1 --json number --jq '.[0].number // empty')

# URL の直後に全角文字を続けると、GitHub の自動リンクがそこまで URL に含めて壊れる（#1034 で実測）。
run_line="- run: ${RUN_URL} （attempt ${RUN_ATTEMPT}）
- コミット: ${HEAD_SHA}
- ブランチ: ${HEAD_BRANCH}（${EVENT}）"

body_file=$(mktemp)
trap 'rm -f "${body_file}"' EXIT

if [ "${kind}" = "success" ]; then
  if [ -z "${open_issue}" ]; then
    echo "開いている ${LABEL} の Issue が無いので何もしません。"
    exit 0
  fi
  printf '%s\n\n%s\n\n%s\n' \
    "デプロイが成功しました。" \
    "${run_line}" \
    "原因を確かめてから、この Issue を閉じてください。" >"${body_file}"
  gh issue comment "${open_issue}" --body-file "${body_file}"
  exit 0
fi

if [ -n "${open_issue}" ]; then
  printf '%s\n\n%s\n' "デプロイがまた失敗しました（${CONCLUSION}）。" "${run_line}" >"${body_file}"
  gh issue comment "${open_issue}" --body-file "${body_file}"
  exit 0
fi

# ラベルが無いと issue create が失敗する。--force で既存なら更新扱いになるので毎回呼んでよい。
gh label create "${LABEL}" --color B60205 \
  --description "Deploy API の失敗（#1019 のワークフローが立てる）" --force

printf '%s\n\n%s\n\n%s\n\n%s\n' \
  "Deploy API が失敗しました（${CONCLUSION}）。本番は直前のリビジョンのまま動いている可能性があります。" \
  "${run_line}" \
  "失敗が続く間は、新しい Issue を立てずにこの Issue へ追記します。デプロイが成功したら追記しますが、自動では閉じません。" \
  "ADR-0089 / #1019" >"${body_file}"
gh issue create --title "デプロイが失敗しました（${HEAD_BRANCH}）" \
  --label "${LABEL}" --assignee "${ASSIGNEE}" --body-file "${body_file}"
