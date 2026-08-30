#!/usr/bin/env bash
# シェルスクリプト中の「非 ASCII 文字の直前で壊れる変数参照」を検査する（#864）。
#
# macOS 標準の bash 3.2 は、$name 形式の変数参照の直後に非 ASCII 文字（全角括弧・全角句読点
# など）が続くと、その文字まで変数名の一部として読む。結果、値が展開されずメッセージが壊れる。
# 波括弧で囲む ${name} 形式なら境界が明示されるので壊れない（bash 3.2.57 / macOS で実測）。
#
# set -u を使っているスクリプトなら unbound variable で即死するので気づける（#857 / PR #858）が、
# 使っていなければ黙って値が消えるだけで通ってしまう。#864 で見つかった
# .claude/hooks/session-start-docker.sh は、Docker の起動に失敗したときにログの場所を
# 表示する行がこれで壊れており、その行の目的がまるごと失われていた。
#
# なお shellcheck はこの形を検出しない（壊れていた行は pre-commit / CI の shellcheck を通っていた）。
# このリポジトリはメッセージを日本語で書く規約（CLAUDE.md「コーディング規約」）なので、
# 変数参照の直後に全角文字が来る形を構造的に踏みやすい。
#
# 検出するもの:
#   - $name 形式の変数参照の直後に非 ASCII バイトが続く箇所（${name} 形式は対象外）
#
# 検出しないもの（既知の穴）:
#   - シェルスクリプト以外の場所に書かれたシェルコマンド（lefthook.yml の run: 等）
#   - $1 / $@ / $? のような 1 文字の特殊パラメータ。名前の続きとしては読まれないため
#     bash 3.2 でも壊れない（実測で確認）
#
# 使い方:
#   scripts/check-shell-brace-expansion.sh [file...]
#   - 引数なしならリポジトリ配下の *.sh を全件検査する（CI 用。root からの実行を前提とする）。
#   - 引数があればそのファイルだけを検査する（lefthook が {staged_files} を渡す）。
# 終了コード: 違反があれば 1、なければ 0。
#
# 互換: 検査される側と同じく macOS 標準の bash 3.2 で動くよう mapfile / 連想配列を使わない。
set -euo pipefail

if [ "$#" -gt 0 ]; then
  list=$(printf '%s\n' "$@")
else
  list=$(find . -name '*.sh' -not -path './.git/*' | sort)
fi

if [ -z "${list}" ]; then
  exit 0
fi

status=0

while IFS= read -r file; do
  [ -n "${file}" ] || continue
  [ -f "${file}" ] || continue

  # 非 ASCII の判定はバイト単位で行う（perl はデコード指定が無ければバイト列として読む）。
  hits=$(perl -ne 'print "  $ARGV:$.: $_" if /\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]/' "${file}") || true
  if [ -n "${hits}" ]; then
    echo "NG: ${file} に、非 ASCII 文字の直前で壊れる変数参照があります。" >&2
    printf '%s\n' "${hits}" >&2
    echo "    bash 3.2 は直後の非 ASCII 文字まで変数名として読みます。波括弧で囲んでください。" >&2
    status=1
  fi
done <<< "${list}"

exit "${status}"
