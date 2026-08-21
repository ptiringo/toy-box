#!/usr/bin/env bash
# Markdown が張る相対リンクの切れを検査する（#806）。
#
# #776 で指示ファイルをリンク委譲型（詳細を再掲せず出所へのリンクで辿らせる形）へ再構成したため、
# リンクが切れると情報がどこにも無い状態になる。主要因は ADR の連番繰り下げ（#597 / #712 の実績）と
# .claude/rules/ の分割・改名で、どちらも静かに壊れて CI は何も言わなかった。
#
# 検査するのは相対リンクの実在だけで、外部 URL とアンカー（#見出し）は見ない。外部 URL は
# ネットワークに出るためレート制限・flaky・オフライン実行の問題が付き、相対リンクのアンカーは
# 導入時点の実測で 0 本だったため守る対象が無い。
#
# 使い方:
#   scripts/check-markdown-links.sh
#   - 引数は取らず、常に追跡下の *.md 全件を検査する。リンク切れは「リンク先が消えた・改名された」
#     ときに起きるが、そのときリンク元の .md は stage されない。{staged_files} だけを見る設計は
#     主要因（ADR の繰り下げ）を狙って取り逃すため、lefthook からも全件で呼ぶ（glob は発火を絞る役）。
#   - リポジトリ root からの実行を前提とする。
# 終了コード: 違反があれば 1、なければ 0。
#
# 互換: macOS 標準の bash 3.2 で動くよう連想配列/mapfile を使わない（check-adr-numbering.sh と同じ）。
set -euo pipefail

status=0

# 追跡下の .md だけを対象にする。gitignore された生成物（build/・node_modules/・
# docs/superpowers/）は git が自然に除くので、除外リストを自前で持たなくて済む。
while IFS= read -r file; do
  [ -n "$file" ] || continue
  dir=$(dirname "$file")

  # コードフェンス内の行を捨て、行内のインラインコードを落としてから `](...)` を行番号つきで拾う。
  # インラインコードを落とすのは、ドキュメントが書く例示（`[NNNN](NNNN-....md)` 等）を
  # 実在チェックに掛けないため。
  while IFS="$(printf '\t')" read -r lineno link; do
    [ -n "$link" ] || continue

    # 外部 URL と純アンカーは対象外（相対リンクの切れだけを見る）。
    case "$link" in
      http://* | https://* | mailto:* | \#*) continue ;;
    esac

    # アンカーは剥がして実体だけを見る。
    path=${link%%#*}
    [ -n "$path" ] || continue

    # ディレクトリへのリンク（例: ../docs/adr/）が実在するので -f ではなく -e で見る。
    if [ ! -e "$dir/$path" ]; then
      echo "NG: $file:$lineno リンク先がありません: $link" >&2
      status=1
    fi
  done < <(awk '
    /^[[:space:]]*```/ { fence = !fence; next }
    fence { next }
    {
      line = $0
      gsub(/`[^`]*`/, "", line)
      while (match(line, /\]\([^)]+\)/)) {
        printf "%d\t%s\n", FNR, substr(line, RSTART + 2, RLENGTH - 3)
        line = substr(line, RSTART + RLENGTH)
      }
    }
  ' "$file")
done < <(git ls-files '*.md')

exit "$status"
