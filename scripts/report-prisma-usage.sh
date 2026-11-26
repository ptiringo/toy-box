#!/usr/bin/env bash
# 本番 DB（Prisma Postgres）の今月の operations の使用量を見て、閾値を超えたら GitHub Issue に残す（#1041）。
#
# Free プランの operations は月 200,000 回が上限で、超えると月が変わるまで DB が接続ごと拒否する。
# 2026-11 は接続プールの既定値が原因で上限に達し、本番が止まるまで気づかなかった。Free プランには
# 上限が近いことを知らせるメールが無いので、Management API の使用量を毎日見て、自分で知らせる。
#
# 振る舞い:
#   - 使用量が閾値（THRESHOLDS）のどれも超えていない: 何もしない
#   - 超えている: ラベル prisma-usage の、その月の Issue が開いていれば追記する。無ければ立てる。
#     同じ閾値では 1 度しか知らせない（毎日同じ内容を追記しない）
#   - 使用量を取得できない（トークンの期限切れ等）: 監視が止まっていることを Issue に残す。
#     開いている取得失敗の Issue があれば、新しく立てずに追記する
#   どの Issue も自動では閉じない。
#
# Project #4 への追加はこのスクリプトでは行わない。GITHUB_TOKEN は Project を操作できないので、
# Project 側の組み込みの自動追加（ラベルで拾う）に任せる。
#
# 使い方（環境変数で受け取る。ワークフローの式をシェルへ直接展開しないため）:
#   PRISMA_TOKEN       Prisma のサービストークン（本番 DB のプロジェクトだけに絞ったもの）
#   PRISMA_DATABASE_ID 本番 DB の ID（db_...）
#   LIMIT              月の operations の上限
#   THRESHOLDS         知らせる閾値（%）。空白区切り・昇順
#   RUN_URL            この run の URL
#   ASSIGNEE           Issue を割り当てるユーザー
#   PRISMA_API_BASE    API の基底 URL（省略時は https://api.prisma.io。検証用に差し替えられる）
#   gh の認証と対象リポジトリは GH_TOKEN / GH_REPO で渡す。
# 終了コード: 取得に失敗したら（Issue に残したうえで）非 0。gh の操作に失敗しても非 0。
#
# 互換: macOS 標準の bash 3.2 で動くよう、非 ASCII 文字に接する変数は ${name} で書く（#864）。
set -euo pipefail

LABEL="prisma-usage"

: "${PRISMA_TOKEN:?PRISMA_TOKEN が要ります}"
: "${PRISMA_DATABASE_ID:?PRISMA_DATABASE_ID が要ります}"
: "${LIMIT:?LIMIT が要ります}"
: "${THRESHOLDS:?THRESHOLDS が要ります}"
: "${RUN_URL:?RUN_URL が要ります}"
: "${ASSIGNEE:?ASSIGNEE が要ります}"
api_base="${PRISMA_API_BASE:-https://api.prisma.io}"

work_dir=$(mktemp -d)
trap 'rm -rf "${work_dir}"' EXIT
usage_file="${work_dir}/usage.json"
body_file="${work_dir}/body.md"

ensure_label() {
  # ラベルが無いと issue create が失敗する。--force で既存なら更新扱いになるので毎回呼んでよい。
  gh label create "${LABEL}" --color D93F0B \
    --description "本番 DB の operations の使用量（#1041 のワークフローが立てる）" --force
}

# 取得に失敗したら、監視が止まっていることを残して非 0 で終える。
report_fetch_failure() {
  local reason="$1"
  local title="Prisma の使用量を取得できませんでした"
  local open_issue
  open_issue=$(gh issue list --label "${LABEL}" --state open --search "in:title \"${title}\"" \
    --limit 1 --json number --jq '.[0].number // empty')

  # URL の直後に全角文字を続けると、GitHub の自動リンクがそこまで URL に含めて壊れる（#1034 で実測）。
  if [ -n "${open_issue}" ]; then
    printf '%s\n\n- run: %s\n- 理由: %s\n' "また取得できませんでした。" "${RUN_URL}" "${reason}" >"${body_file}"
    gh issue comment "${open_issue}" --body-file "${body_file}"
  else
    ensure_label
    printf '%s\n\n- run: %s\n- 理由: %s\n\n%s\n\n%s\n' \
      "本番 DB の operations の使用量を取得できませんでした。直るまで、上限に近づいても知らせられません。" \
      "${RUN_URL}" "${reason}" \
      "よくある原因はサービストークンの期限切れです。Prisma Console で本番 DB のプロジェクトに絞ったトークンを作り直し、シークレット PRISMA_USAGE_TOKEN を更新してください。" \
      "#1041" >"${body_file}"
    gh issue create --title "${title}" --label "${LABEL}" --assignee "${ASSIGNEE}" --body-file "${body_file}"
  fi
  exit 1
}

# 応答の本文はエラー時も表示しない（トークンは含まれないが、何が返るかを保証できないため）。
status=$(curl -sS -o "${usage_file}" -w '%{http_code}' \
  -H "Authorization: Bearer ${PRISMA_TOKEN}" \
  "${api_base}/v1/databases/${PRISMA_DATABASE_ID}/usage") || report_fetch_failure "API に接続できませんでした"
if [ "${status}" != "200" ]; then
  report_fetch_failure "API が HTTP ${status} を返しました"
fi

used=$(jq -er '.metrics.operations.used | numbers' "${usage_file}") ||
  report_fetch_failure "応答に operations の使用量が含まれていませんでした"
period_start=$(jq -er '.period.start' "${usage_file}") ||
  report_fetch_failure "応答に集計期間が含まれていませんでした"
month="${period_start:0:7}"

# used は整数で返る。小数で返っても切り捨てで比べる。
used=${used%.*}
percent=$((used * 100 / LIMIT))
echo "${month} の operations: ${used} / ${LIMIT}（${percent}%）"

crossed=""
for t in ${THRESHOLDS}; do
  if [ "${percent}" -ge "${t}" ]; then
    crossed="${t}"
  fi
done
if [ -z "${crossed}" ]; then
  echo "閾値を超えていないので何もしません。"
  exit 0
fi

title="Prisma の operations が上限に近づいています（${month}）"
open_issue=$(gh issue list --label "${LABEL}" --state open --search "in:title \"${title}\"" \
  --limit 1 --json number --jq '.[0].number // empty')

# 同じ閾値を 2 度知らせないよう、知らせた閾値を HTML コメントの印として本文に残す。
marker="<!-- prisma-usage-threshold:${crossed} -->"
line="- 使用量: ${used} / ${LIMIT}（${percent}%。閾値 ${crossed}% を超えました）
- run: ${RUN_URL}"

if [ -n "${open_issue}" ]; then
  if gh issue view "${open_issue}" --json body,comments --jq '[.body, .comments[].body] | join("\n")' |
    grep -qF -- "${marker}"; then
    echo "閾値 ${crossed}% は #${open_issue} で知らせ済みなので何もしません。"
    exit 0
  fi
  printf '%s\n\n%s\n\n%s\n' "使用量が閾値 ${crossed}% を超えました。" "${line}" "${marker}" >"${body_file}"
  gh issue comment "${open_issue}" --body-file "${body_file}"
  exit 0
fi

ensure_label
printf '%s\n\n%s\n\n%s\n\n%s\n\n%s\n' \
  "本番 DB（Prisma Postgres）の今月の operations が、上限の ${crossed}% を超えました。上限に達すると、月が変わるまで DB が接続ごと拒否し、本番が止まります。" \
  "${line}" \
  "Prisma Console で日ごとの伸びを確かめ、想定外の消費が無いか調べてください。今月のうちは、より高い閾値を超えたときだけこの Issue に追記します。自動では閉じません。" \
  "#1041" \
  "${marker}" >"${body_file}"
gh issue create --title "${title}" --label "${LABEL}" --assignee "${ASSIGNEE}" --body-file "${body_file}"
