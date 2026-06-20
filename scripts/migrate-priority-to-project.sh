#!/usr/bin/env bash
# 優先度ラベル（P1〜P4）を GitHub Projects #4 の Priority フィールドへ移行するスクリプト。
#
# 背景・方針は ADR-0011 / CLAUDE.md「優先度管理」を参照。Projects のカスタムフィールドを
# 優先度の唯一の出所とし、P ラベルは廃止する（完全移行）。処理は 2 段階:
#   1. open issue を Project に投入し、各 issue の `P1: 今すぐ`〜`P4: 探索・保留` ラベルを
#      Priority single-select フィールドの対応オプションへ写像する。
#   2. 写像後、リポジトリから P1〜P4 ラベル定義を削除する（全 issue から一括除去される）。
#
# 順序が重要: ラベルを field へ写し終えてから定義を消す。先に消すと写像元が失われる。
# 冪等: 既に投入済みの issue は item-add が既存アイテムを返すため、再実行しても重複しない。
#       ラベル削除後の再実行は対象 0 件になるだけで安全（field 値は既に設定済み）。
#
# 前提: `gh auth refresh -s project` で project スコープ付与済みであること。
# 実行: **通常のターミナル（Terminal.app / iTerm 等）で** bash scripts/migrate-priority-to-project.sh
#   ※ Claude Code の `!` 経由やサンドボックス下で実行すると、スクリプト内の gh が
#     サンドボックスのプロキシ TLS に阻まれ `OSStatus -26276` で全 API 呼び出しが失敗する
#     （gh の sandbox 除外は単体コマンドのみ対象で、スクリプト内の gh には効かない）。
#     必ずサンドボックス外の素のシェルで実行すること。
# 互換: macOS 標準の bash 3.2 で動くよう連想配列/mapfile を使わない。
set -euo pipefail

# --- プリフライト: API 到達性を確認（TLS 失敗を黙って素通りさせない）---
if ! gh api graphql -f query='query{viewer{login}}' >/dev/null 2>&1; then
  echo "ERROR: GitHub API に到達できません（TLS/認証エラーの可能性）。" >&2
  echo "  - サンドボックス外の通常ターミナルで実行していますか？（OSStatus -26276 は sandbox TLS 起因）" >&2
  echo "  - project スコープはありますか？ 必要なら: gh auth refresh -s project" >&2
  exit 1
fi

OWNER=ptiringo
PROJECT_NUMBER=4
PROJECT_ID=PVT_kwHOAGtZ7c4BbL04
FIELD_ID=PVTSSF_lAHOAGtZ7c4BbL04zhV-LEM

# P ラベル名 -> Priority オプション ID
option_id_for() {
  case "$1" in
    "P1: 今すぐ") echo e9ce2b26 ;;
    "P2: 近いうち") echo 61f24689 ;;
    "P3: いずれ") echo 1d0a06c9 ;;
    "P4: 探索・保留") echo 12ac3381 ;;
    *) echo "" ;;
  esac
}

# open issue を「URL <TAB> Pラベル」で列挙（P ラベルは最初の 1 件のみ採用）。
# process substitution（done < <(gh ...)）だと gh の失敗が握りつぶされ「0 件」で
# 誤完走するため、一旦変数へ取得して set -e で fail-fast させる。
# jq プログラム内の $i / $p は jq 変数。shell 展開させないため意図的に single quote。
# shellcheck disable=SC2016
issues=$(
  gh issue list --state open --limit 200 --json url,labels \
    --jq '.[] | . as $i
          | ([$i.labels[].name | select(test("^P[1-4]:"))] | first) as $p
          | select($p != null)
          | "\($i.url)\t\($p)"'
)

count=0
# herestring は while をサブシェル化しないため count の加算が親に残る
while IFS=$'\t' read -r url plabel; do
  [ -z "$url" ] && continue
  optid=$(option_id_for "$plabel")
  if [ -z "$optid" ]; then
    echo "skip（未知の P ラベル '$plabel'）: $url"
    continue
  fi
  item_id=$(gh project item-add "$PROJECT_NUMBER" --owner "$OWNER" --url "$url" --format json --jq '.id')
  gh project item-edit \
    --id "$item_id" \
    --project-id "$PROJECT_ID" \
    --field-id "$FIELD_ID" \
    --single-select-option-id "$optid" >/dev/null
  count=$((count + 1))
  echo "set $plabel -> $url"
done <<< "$issues"

echo "写像完了: $count 件を投入・設定。確認: gh project item-list $PROJECT_NUMBER --owner $OWNER"

# --- 第2段階: P ラベル定義を削除（完全移行）---
# ラベル定義を消すと open/closed 問わず全 issue から当該ラベルが外れる。
# 既存ラベル一覧を 1 度だけ取得し、存在するものだけ削除する。削除自体の失敗は
# set -e で fail-fast させる（TLS/権限エラーを「未存在」と誤魔化さない）。
existing_labels=$(gh label list --limit 200 --json name --jq '.[].name')
for label in "P1: 今すぐ" "P2: 近いうち" "P3: いずれ" "P4: 探索・保留"; do
  if printf '%s\n' "$existing_labels" | grep -qxF "$label"; then
    gh label delete "$label" --yes >/dev/null
    echo "delete label: $label"
  else
    echo "skip（ラベル既に未存在）: $label"
  fi
done

echo "完了: 優先度を Project #$PROJECT_NUMBER の Priority フィールドへ移行し、P ラベルを廃止した。"
