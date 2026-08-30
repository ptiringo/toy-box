#!/usr/bin/env bash
# mise.lock の http バックエンドツールが壊れていないかを検査する（#762 / #857）。
#
# mise.lock の options は**ツール単位**（[tools.<tool>.options]）でしか表現できない。一方
# http:kotlin-lsp は macOS だけ format = "zip" を要求する（JetBrains の mac 配布が .sit で、
# 拡張子からは zip と推論されないため）。platform 間で options が食い違うと 1 エントリに
# 収まらず、mise は同じツールを複数の [[tools."http:kotlin-lsp"]] エントリへ割る。これは
# upstream で意図された挙動として入っている（jdx/mise#10999 "disjoint platform variants
# remain separate"）ので、mise 側の修正を待っても解消しない。
#
# 結果として `mise install` は、触ってすらいないツールの lock を 2 通りに壊す
# （mise 2026.8.12 / macOS-x64 で実測）:
#   1. ツール名を指定した install → macOS 分が別エントリへ切り出され、エントリが 2 つになる
#   2. 引数なしの install（`mise bootstrap` が流す実体）→ 逆に macOS の 1 エントリだけが残り、
#      Linux 4 platform の checksum / URL が lock から消える。この lock が main へ入ると
#      Linux 側の `mise install --locked` が解決できなくなる（#584 と同型）
#
# lock の正は「http ツールごとに [[tools]] エントリが 1 つ、mise.toml が宣言する platform が
# すべて載っていて、checksum の形式が mise.toml の宣言と揃っている」状態とする。`mise install`
# の後に出た lock の差分は本題と無関係なので `git checkout -- mise.lock` で捨てること。
# バージョンを上げるときだけ、per-platform の checksum / URL を手で揃える
# （mise.toml 側のコメントも参照）。
#
# 検出するもの:
#   1. [[tools."http:NAME"]] エントリの二重化・欠落
#   2. mise.toml が宣言する platform の lock からの欠落
#   3. checksum の形式（sha256 / blake3 等の algo）が mise.toml の宣言と食い違うこと（#857）。
#      `mise install --force` は lock の checksum を blake3 で書き直す（mise 2026.8.12 で実測）。
#      blake3 も有効な checksum で `--locked` が壊れるわけではないが、本題と無関係な差分が PR に
#      混ざる。#762 で潰したかったのは、まさにこの「触っていないのに lock が汚れる」状態
#
# 検出しないもの（既知の穴）:
#   - checksum の**値**が配布物と一致するか。ここでは形式しか見ない（値の照合は install 時に
#     mise 自身が行う）
#   - http 以外の backend（aqua / ubi / registry）の lock エントリ。per-platform の url / checksum を
#     mise.toml 側に持たないため、lock と突き合わせる相手がそもそも無い
#   - lock 側にしか無い platform（musl 派生）の checksum 形式は、mise.toml が宣言する形式が
#     ツール内で 1 種類のときだけ照合する。混在していれば期待値を決められないので飛ばす
#
# 使い方:
#   scripts/check-mise-lock-platforms.sh
#   - 引数は取らず常に全件を検査する。壊れるのは「触っていないツール」の側なので、
#     lefthook からも {staged_files} は渡さない（glob は発火の条件だけを絞る役）。
#   - リポジトリ root からの実行を前提とする。
# 終了コード: 違反があれば 1、なければ 0。
#
# 互換: macOS 標準の bash 3.2 で動くよう連想配列/mapfile を使わない。
set -euo pipefail

CONFIG="mise.toml"
LOCK="mise.lock"

if [ ! -f "$CONFIG" ]; then
  echo "NG: $CONFIG がありません（リポジトリ root から実行してください）。" >&2
  exit 1
fi
if [ ! -f "$LOCK" ]; then
  echo "NG: $LOCK がありません。" >&2
  exit 1
fi

# TOML セクション（$2 の見出し行）の直下にある checksum の algo（"sha256:..." の sha256）を出す。
# 見つからなければ何も出さない。セクションは次の見出し行（^[）で終わる。
checksum_algo() {
  awk -v header="$2" '
    $0 == header { in_section = 1; next }
    /^\[/ { in_section = 0 }
    in_section && $1 == "checksum" {
      if (match($0, /"[^":]+:/)) {
        print substr($0, RSTART + 1, RLENGTH - 2)
        exit
      }
    }
  ' "$1"
}

# mise.toml が宣言する http バックエンドのツール名（http:NAME）。
# 検査対象を http に絞るのは、per-platform の url / checksum を mise.toml 側で持つのが
# この backend だけだからで（aqua / ubi / registry 経由は mise がレジストリから解決する）、
# 「宣言した platform が lock に揃っているか」を照合できる相手もここに限られる。
tools=$(grep -oE '^\[tools\."http:[^"]+"\]' "$CONFIG" | sed -e 's/^\[tools\."//' -e 's/"\]$//' | sort -u) || true

if [ -z "$tools" ]; then
  echo "NG: $CONFIG に http バックエンドのツール宣言（[tools.\"http:...\"]）が 1 つもありません。" >&2
  echo "    この検査の前提が崩れています。意図的に廃止したならこのスクリプトごと外してください。" >&2
  exit 1
fi

status=0

for tool in $tools; do
  # 1. [[tools."http:NAME"]] のエントリ数。1 でなければ二重化（または未 lock）。
  entries=$(grep -cF "[[tools.\"$tool\"]]" "$LOCK") || true
  if [ "$entries" -eq 0 ]; then
    echo "NG: $LOCK に $tool のエントリがありません。" >&2
    echo "    \`mise install \"$tool\"\` で lock を作ってからコミットしてください。" >&2
    status=1
    continue
  fi
  if [ "$entries" -gt 1 ]; then
    echo "NG: $LOCK の $tool が $entries 個のエントリに分裂しています（期待は 1 個）。" >&2
    echo "    platform ごとに options が食い違うと mise が別エントリへ割ります（仕様）。" >&2
    echo "    \`git checkout -- $LOCK\` で戻してください（この差分は捨ててよい）。" >&2
    status=1
    continue
  fi

  # 2. mise.toml が宣言する platform が lock にすべて載っているか。
  #    lock 側は musl 派生（linux-x64-musl 等）を自動で足すため、包含だけを見る。
  declared=$(grep -F "[tools.\"$tool\".platforms." "$CONFIG" | sed -e 's/^.*\.platforms\.//' -e 's/\]$//' | sort -u) || true
  if [ -z "$declared" ]; then
    echo "NG: $CONFIG の $tool に per-platform の宣言（[tools.\"$tool\".platforms.*]）がありません。" >&2
    status=1
    continue
  fi
  locked=$(grep -F "[tools.\"$tool\".\"platforms." "$LOCK" | sed -e 's/^.*\."platforms\.//' -e 's/"\]$//' | sort -u) || true

  missing=$(comm -23 <(printf '%s\n' "$declared") <(printf '%s\n' "$locked")) || true
  if [ -n "$missing" ]; then
    echo "NG: $LOCK の $tool から platform が欠けています:" >&2
    for p in $missing; do
      echo "    $p" >&2
    done
    echo "    引数なしの \`mise install\` は他 platform の checksum / URL を削除します。" >&2
    echo "    この lock が main へ入ると、欠けた platform で \`mise install --locked\` が壊れます。" >&2
    echo "    \`git checkout -- $LOCK\` で戻してください（この差分は捨ててよい）。" >&2
    status=1
  fi

  # 3. checksum の形式（algo）が mise.toml の宣言と一致するか（#857）。
  #    mise.toml が宣言している platform は 1 対 1 で照合する。
  algos=""
  for p in $declared; do
    want=$(checksum_algo "$CONFIG" "[tools.\"$tool\".platforms.$p]")
    # mise.toml が checksum を書いていない platform は照合対象にしない（期待値が無い）。
    [ -n "$want" ] || continue
    algos="$algos
$want"
    got=$(checksum_algo "$LOCK" "[tools.\"$tool\".\"platforms.$p\"]")
    # 欠落は検査 2 が報告済みなので、ここでは形式の食い違いだけを見る。
    [ -n "$got" ] || continue
    if [ "$got" != "$want" ]; then
      echo "NG: $LOCK の $tool ($p) の checksum が $got 形式です（$CONFIG の宣言は ${want}）。" >&2
      echo "    \`mise install --force\` は lock の checksum を書き直します。" >&2
      echo "    \`git checkout -- $LOCK\` で戻してください（この差分は捨ててよい）。" >&2
      status=1
    fi
  done

  # 宣言側の形式が 1 種類なら、lock 側にしか無い platform（musl 派生）も同じ形式のはず。
  # 混在しているツール、および 1 つも checksum を宣言していないツールでは期待値を決められない
  # ので照合しない。
  expected=$(printf '%s\n' "$algos" | grep -v '^$' | sort -u) || true
  [ -n "$expected" ] || continue
  if [ "$(printf '%s\n' "$expected" | wc -l)" -ne 1 ]; then
    continue
  fi
  extra=$(comm -13 <(printf '%s\n' "$declared") <(printf '%s\n' "$locked")) || true
  for p in $extra; do
    got=$(checksum_algo "$LOCK" "[tools.\"$tool\".\"platforms.$p\"]")
    [ -n "$got" ] || continue
    if [ "$got" != "$expected" ]; then
      echo "NG: $LOCK の $tool ($p) の checksum が $got 形式です（$CONFIG の宣言は ${expected}）。" >&2
      echo "    \`mise install --force\` は lock の checksum を書き直します。" >&2
      echo "    \`git checkout -- $LOCK\` で戻してください（この差分は捨ててよい）。" >&2
      status=1
    fi
  done
done

exit "$status"
