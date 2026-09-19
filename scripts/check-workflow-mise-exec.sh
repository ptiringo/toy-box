#!/usr/bin/env bash
# GitHub Actions のワークフローが `mise exec` 経由でツールを呼んでいないかを検査する（#917）。
#
# mise-action は install したツールの bin ディレクトリを PATH に足す（shims も PATH に載る）ので、
# 後段のステップはコマンドを直接呼べる。にもかかわらず `mise exec -- <cmd>` を挟むと、mise は
# config 上の全ツールを解決しにいき、その場で install する。壊れ方は 3 つ:
#
#   - `install_args` でジョブごとに絞ったはず（#464）の効果が後段で無効化される。実測では
#     editorconfig-check が `installed 1 tool` の直後に `mise exec -- ec` で
#     `installed 19 tools in 7.3s` を出していた（run 34693138310）
#   - mise-action のキャッシュ保存は `mise install` の直後に行われるため、`mise exec` が入れた分は
#     キャッシュに乗らない。毎 run 入れ直しになる
#   - 上流障害の影響面が広がる。#904 で editorconfig-check が落ちたのは install_args に無いツールの
#     attestation 検証が失敗したためで、絞れていれば巻き込まれずに済んだ
#
# 検査対象は .github/workflows/*.yml の **`run:` の中身だけ**。`name:` 等の他のキーに書かれた
# `mise exec` は実行されないので見ない（この 2 つを区別せずに字面を見ると、`name: Workflow mise
# exec Check` のようなジョブ名で自分自身が落ちる。実装時に踏んだ）。
#
# **lefthook.yml は対象外**。ローカルのシェルは mise の PATH が通っている保証がなく、
# actionlint / zizmor / gitleaks は意図的に `mise exec` 経由で呼んでいる。この非対称は「CI では
# mise-action が PATH を整えるが、ローカルでは誰も整えない」ことに由来する。
#
# 検出しないもの（既知の穴）:
#   - ワークフローが呼ぶシェルスクリプトの中の `mise exec`。検査は YAML の字面だけを見る
#   - `mise exec` 以外で config 全体を解決させる経路（`mise run` 等）
#   - run ブロックの追跡が外れる書き方（YAML アンカー、複数行のフロースカラ等）。追跡は
#     「`run:` の行」と「`run: |` のインデントより深い行」の 2 つだけを見る素朴なもの
#   - `run:` の行末コメント（`run: foo  # mise exec は使わない` 等）は逆に誤検出する。
#     コメント判定は行頭の `#` だけを見るため（検査が緩む側ではないので許容する）
#
# 使い方:
#   scripts/check-workflow-mise-exec.sh [file...]
#   - 引数なしなら .github/workflows/*.yml を全件検査する（CI 用。root からの実行を前提とする）。
#   - 引数があればそのファイルだけを検査する。
# 終了コード: 違反があれば 1、なければ 0。
#
# 互換: 他の検査スクリプトと揃え、macOS 標準の bash 3.2 で動くよう mapfile / 連想配列を使わない。
set -euo pipefail

if [ "$#" -gt 0 ]; then
  list=$(printf '%s\n' "$@")
elif [ -d .github/workflows ]; then
  list=$(find .github/workflows -name '*.yml' | sort)
else
  echo "NG: .github/workflows がありません（リポジトリ root から実行してください）。" >&2
  exit 1
fi

if [ -z "${list}" ]; then
  exit 0
fi

status=0

while IFS= read -r file; do
  [ -n "${file}" ] || continue
  [ -f "${file}" ] || continue

  hits=$(awk '
    BEGIN { in_block = 0; block_indent = -1 }
    {
      match($0, /^ */)
      indent = RLENGTH
      blank = ($0 ~ /^[ \t]*$/)

      # ブロックスカラの継続判定。空行はブロックを終わらせない（YAML の仕様どおり）。
      if (in_block && !blank && indent <= block_indent) { in_block = 0 }
      target = (in_block && !blank)

      # `run:` の行そのもの（`- run:` も含む）。`|` / `>` ならブロックスカラの開始。
      if ($0 ~ /^[ ]*(- )?run:/) {
        target = 1
        if ($0 ~ /run:[ ]*[|>]/) { in_block = 1; block_indent = indent }
        else { in_block = 0 }
      }

      if (!target) { next }
      if ($0 ~ /^[ \t]*#/) { next }
      if ($0 ~ /(^|[^[:alnum:]_.-])mise[ \t]+(exec|x)([ \t]|$)/) { printf "%d:%s\n", NR, $0 }
    }
  ' "${file}")

  if [ -n "${hits}" ]; then
    echo "NG: ${file} が mise exec 経由でツールを呼んでいます。" >&2
    printf '%s\n' "${hits}" | sed 's/^/  /' >&2
    echo "    mise-action が install 先の bin を PATH に足すので直接呼べます。mise exec は config 上の" >&2
    echo "    全ツールを install してしまい、install_args の絞り込み（#464）を無効化します（#917）。" >&2
    status=1
  fi
done <<< "${list}"

exit "${status}"
