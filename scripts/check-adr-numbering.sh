#!/usr/bin/env bash
# ADR（docs/adr/）の採番と索引の整合を検査する（#757）。
#
# ADR のファイル名は番号が同じでもサフィックスが違えば別ファイルなので、二重採番が起きても
# git は衝突を検出しない。索引（README.md）の行が離れていれば両方が自動マージされるため、
# 二重採番のまま main に入りうる（#597 が実際にこの経路で入った）。検知を運任せにしない。
#
# 検査する 3 点:
#   1. NNNN の重複が無い（#597 / #712 の直接の再発防止）
#   2. README.md の索引が張る相対リンクの先が実在する（繰り下げ後の直し忘れ）
#   3. すべての ADR が索引に載っている（索引への追加漏れ）
#
# 使い方:
#   scripts/check-adr-numbering.sh
#   - 引数は取らず常に全件を検査する。重複はリポジトリ全体を見ないと判定できないため、
#     lefthook からも {staged_files} を渡さない（glob で発火だけを絞る）。
#   - リポジトリ root からの実行を前提とする。
# 終了コード: 違反があれば 1、なければ 0。
#
# 互換: macOS 標準の bash 3.2 で動くよう連想配列/mapfile を使わない。
set -euo pipefail

ADR_DIR="docs/adr"
INDEX="$ADR_DIR/README.md"

if [ ! -d "$ADR_DIR" ]; then
  echo "NG: $ADR_DIR がありません（リポジトリ root から実行してください）。" >&2
  exit 1
fi
if [ ! -f "$INDEX" ]; then
  echo "NG: $INDEX がありません。" >&2
  exit 1
fi

# ADR のファイル名一覧（1 行 1 件）。README.md は 4 桁始まりではないので自然に外れる。
adrs=""
for f in "$ADR_DIR"/[0-9][0-9][0-9][0-9]-*.md; do
  [ -e "$f" ] || continue
  adrs="$adrs$(basename "$f")
"
done

if [ -z "$adrs" ]; then
  echo "NG: $ADR_DIR に ADR（NNNN-*.md）が 1 つもありません。" >&2
  exit 1
fi

status=0

# 1. 番号の重複
dups=$(printf '%s' "$adrs" | cut -c1-4 | sort | uniq -d) || true
for n in $dups; do
  echo "NG: ADR 番号 $n が二重採番されています:" >&2
  for f in "$ADR_DIR/$n"-*.md; do
    echo "    $f" >&2
  done
  echo "    どちらかを未使用の番号へ繰り下げ、$INDEX の索引と本文中の参照も揃えてください。" >&2
  status=1
done

# 2. 索引が張るリンク先の実在
#    索引の相対リンクは docs/adr/ からの相対パス。アンカー（#見出し）が付いても実体だけを見る。
links=$(grep -oE '\]\([^)]+\)' "$INDEX" | sed -e 's/^](//' -e 's/)$//' -e 's/#.*$//' | sort -u) || true
for l in $links; do
  # 外部 URL は対象外（相対リンクの切れだけを見る）
  case "$l" in
    http://* | https://* | mailto:*) continue ;;
  esac
  if [ ! -e "$ADR_DIR/$l" ]; then
    echo "NG: $INDEX の索引リンク先がありません: $l" >&2
    status=1
  fi
done

# 3. 索引への掲載漏れ
while IFS= read -r base; do
  [ -n "$base" ] || continue
  if ! grep -qF "($base)" "$INDEX"; then
    echo "NG: $ADR_DIR/$base が $INDEX の索引に載っていません。" >&2
    status=1
  fi
done <<EOF
$adrs
EOF

exit "$status"
