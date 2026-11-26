#!/usr/bin/env bash
# 2 つの OpenAPI 仕様（base / head）の差分を Markdown で標準出力へ書き出す（#1085 / ADR-0093）。
#
# 目的: 依存更新などで生成される仕様が意図せず変わったことを PR 上で見えるようにする。
# PR #1068（springdoc-openapi 3.2.1 / spring-ai 2.0.3）では `required` 配列が 39 → 3、サブタイプから
# 親への `allOf` 参照が 10 → 0 に減ったが、vacuum lint は仕様の「品質」しか見ず「前の版との差分」は
# 見ないため CI は緑のままだった。
#
# 出すもの（上から順に）:
#   1. 構造の指標（paths / components.schemas / `required` 配列 / `allOf` の出現数）の base → head。
#      #1068 の退行はこの数字だけで気づける。oasdiff の判定規則に頼らない素朴な網として置く
#   2. oasdiff の検出件数（重大度 × チェック ID）。一覧は数百行になりうるので、まず件数で掴む
#   3. oasdiff breaking（ERR / WARN）の一覧と changelog（INFO を含む全件）: 折りたたみ
#   4. `jq -S` で正規化した JSON の unified diff: 折りたたみ・行数上限つき。oasdiff が拾わない変更
#      （description・未参照の schema 等）の最後の受け皿
#
# 失敗しないこと: この出力は可視化用で、差分の有無で終了コードを変えない（落とすかどうかは ADR-0093
# の「今後の判断」）。oasdiff が仕様を読めなかったときも、その旨を本文に書いて 0 で抜ける。
#
# 安全側の設定: oasdiff は既定で外部 $ref を取りにいく（`--allow-external-refs` の既定が true）。
# springdoc の出力は外部参照を持たないので false にして、仕様の中身次第で外へ通信しないようにする。
#
# 使い方:
#   scripts/openapi-spec-diff.sh <base.json> <head.json> [base-label] [head-label]
#   - oasdiff と jq が PATH にあること（CI は mise-action が oasdiff を、runner が jq を供給する）。
#   - CI では出力を "$GITHUB_STEP_SUMMARY" へ追記する（.github/workflows/openapi-diff.yml）。
# 終了コード: 引数の誤り・入力ファイルの欠落のときだけ 2。それ以外は 0。
#
# 互換: 他の検査スクリプトと揃え、macOS 標準の bash 3.2 で動くよう mapfile / 連想配列を使わない。
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <base.json> <head.json> [base-label] [head-label]" >&2
  exit 2
fi

base=$1
head=$2
base_label=${3:-base}
head_label=${4:-head}

for f in "$base" "$head"; do
  if [ ! -f "$f" ]; then
    echo "error: $f が見つからない" >&2
    exit 2
  fi
done

# 統合 diff の上限。Job Summary は 1 ステップあたり 1MiB が上限で、超えると summary ごと捨てられる。
max_diff_lines=400

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

jq -S . "$base" > "$tmp/base.json"
jq -S . "$head" > "$tmp/head.json"

# 構造の指標。`required` / `allOf` は仕様中のどこに現れても数える（#1068 の退行は schema 全域に及んだ）。
metric() {
  local file=$1 expr=$2
  jq "$expr" "$file"
}
row() {
  local name=$1 expr=$2 b h mark
  b=$(metric "$tmp/base.json" "$expr")
  h=$(metric "$tmp/head.json" "$expr")
  mark=""
  if [ "$b" != "$h" ]; then
    mark="⚠️"
  fi
  echo "| $name | $b | $h | $mark |"
}

echo "## OpenAPI 仕様の差分（${base_label} → ${head_label}）"
echo

if cmp -s "$tmp/base.json" "$tmp/head.json"; then
  echo "差分なし（\`jq -S\` で正規化した JSON が一致）。"
  exit 0
fi

echo "| 指標 | ${base_label} | ${head_label} | |"
echo "|---|---:|---:|---|"
row "paths" '.paths // {} | length'
row "operations" '[.paths // {} | .[] | keys[] | select(IN("get","put","post","delete","patch","head","options","trace"))] | length'
row "components.schemas" '.components.schemas // {} | length'
row "\`required\` 配列" '[.. | objects | select(has("required") and (.required | type) == "array")] | length'
row "\`required\` の要素数" '[.. | objects | select(has("required") and (.required | type) == "array") | .required[]] | length'
row "\`allOf\`" '[.. | objects | select(has("allOf"))] | length'
row "\`oneOf\`" '[.. | objects | select(has("oneOf"))] | length'
# shellcheck disable=SC2016 # jq の式であってシェル変数ではない
row "\`\$ref\`" '[.. | objects | select(has("$ref"))] | length'
echo

oasdiff_opts=(--allow-external-refs=false)

# oasdiff の markdown 出力の先頭にある `# API Changelog <ver> vs. <ver>` は h1 で、Job Summary の
# 見出し階層を崩すので落とす。
strip_title() {
  sed '/^# API Changelog/d'
}

# 失敗したら本文にエラーを出して続ける（可視化のための出力なので、ここで止めない）。
print_oasdiff_error() {
  echo "oasdiff が失敗した:"
  echo
  echo '```'
  cat "$1"
  echo '```'
}

# 重大度（oasdiff の level: 3=ERR / 2=WARN / 1=INFO）とチェック ID ごとの件数。
# 一覧は数百行になりうる（#1068 では 308 件）ので、まず件数で「何が起きたか」を掴めるようにする。
echo "### oasdiff の検出件数"
echo
if oasdiff changelog "${oasdiff_opts[@]}" -f json "$tmp/base.json" "$tmp/head.json" > "$tmp/changelog.json" 2> "$tmp/changelog.err"; then
  if [ "$(jq 'length' "$tmp/changelog.json")" -eq 0 ]; then
    echo "なし（oasdiff は意味のある変更を検出しなかった。下の生 diff を参照）。"
  else
    jq -r '
      def lv: if . == 3 then "ERR" elif . == 2 then "WARN" else "INFO" end;
      "ERR \([.[] | select(.level == 3)] | length) / WARN \([.[] | select(.level == 2)] | length) / INFO \([.[] | select(.level == 1)] | length)",
      "",
      "| level | check | 件数 |",
      "|---|---|---:|",
      (group_by([-.level, .id])[] | "| \(.[0].level | lv) | `\(.[0].id)` | \(length) |")
    ' "$tmp/changelog.json"
  fi
else
  print_oasdiff_error "$tmp/changelog.err"
fi
echo

echo "<details><summary>破壊的変更の一覧（oasdiff breaking: ERR / WARN）</summary>"
echo
if oasdiff breaking "${oasdiff_opts[@]}" -f markdown "$tmp/base.json" "$tmp/head.json" > "$tmp/breaking.md" 2> "$tmp/breaking.err"; then
  if [ -s "$tmp/breaking.md" ]; then
    strip_title < "$tmp/breaking.md"
  else
    echo "なし。"
  fi
else
  print_oasdiff_error "$tmp/breaking.err"
fi
echo
echo "</details>"
echo

echo "<details><summary>変更履歴の全件（oasdiff changelog: INFO を含む）</summary>"
echo
if oasdiff changelog "${oasdiff_opts[@]}" -f markdown "$tmp/base.json" "$tmp/head.json" > "$tmp/changelog.md" 2> "$tmp/changelog.err"; then
  if [ -s "$tmp/changelog.md" ]; then
    strip_title < "$tmp/changelog.md"
  else
    echo "なし。"
  fi
else
  print_oasdiff_error "$tmp/changelog.err"
fi
echo
echo "</details>"
echo

# diff は差分があると 1 を返すので、set -e で落ちないよう `|| true` で受ける。
diff -u --label "$base_label" --label "$head_label" "$tmp/base.json" "$tmp/head.json" > "$tmp/raw.diff" || true
total=$(wc -l < "$tmp/raw.diff" | tr -d ' ')
echo "<details><summary>正規化 JSON の生 diff（${total} 行）</summary>"
echo
echo '```diff'
head -n "$max_diff_lines" "$tmp/raw.diff"
echo '```'
if [ "$total" -gt "$max_diff_lines" ]; then
  echo
  echo "（先頭 ${max_diff_lines} 行で打ち切り。全体は Artifact の openapi-spec-diff を参照）"
fi
echo
echo "</details>"
