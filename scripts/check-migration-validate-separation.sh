#!/usr/bin/env bash
# 同一マイグレーションファイル内に同名制約の ADD CONSTRAINT と VALIDATE CONSTRAINT が
# 同居していないかを検査する（ADR-0052 / #539）。
#
# Flyway は各マイグレーションを 1 トランザクションで適用するため、ADD CONSTRAINT が取得した
# ACCESS EXCLUSIVE ロックはコミットまで保持され、同一トランザクション内の VALIDATE CONSTRAINT は
# SHARE UPDATE EXCLUSIVE へ格下げされない（無停止効果が出ない）。VALIDATE は必ず後続の
# 別マイグレーションファイルへ分離する。
#
# 使い方:
#   scripts/check-migration-validate-separation.sh [file...]
#   - 引数あり: 指定ファイルのみ検査（lefthook の {staged_files} 用）。
#     db/migration/ 配下の .sql 以外は黙ってスキップする。
#   - 引数なし: src/main/resources/db/migration/V*.sql 全件を検査（CI 用）。
#     リポジトリ root からの実行を前提とする。
# 終了コード: 違反があれば 1、なければ 0。
#
# 互換: macOS 標準の bash 3.2 で動くよう連想配列/mapfile を使わない。
set -euo pipefail

# 既知例外（baseline）: 規約制定（ADR-0052）以前に本番へ適用済みのファイル。
# コメントのみの変更でも Flyway チェックサムが変わり validate-on-migrate が落ちるため
# 編集できず、同居したまま残す（実害は lock_timeout=5s と少行数で抑えられている）。
BASELINE="V6__add_breeding_registration_retirement_check.sql V8__add_breeding_result_report_submission.sql V9__add_version_not_null.sql"

if [ "$#" -eq 0 ]; then
  set -- src/main/resources/db/migration/V*.sql
fi

status=0
for f in "$@"; do
  base=$(basename "$f")

  # マイグレーション SQL 以外はスキップ（lefthook から他の .sql が渡られても無視する）
  case "$f" in
    *db/migration/*.sql) ;;
    *) continue ;;
  esac

  # baseline は検査対象外
  case " $BASELINE " in
    *" $base "*) continue ;;
  esac

  # 改行・連続空白を単一スペースへ畳み、複数行に跨る DDL も一律に照合できるようにする
  content=$(tr -s '[:space:]' ' ' <"$f")

  # VALIDATE CONSTRAINT の対象制約名を抽出する（無ければこのファイルは合格）
  names=$(printf '%s' "$content" \
    | grep -oiE 'VALIDATE CONSTRAINT [a-z0-9_]+' \
    | awk '{print tolower($3)}' | sort -u) || continue

  for name in $names; do
    # 同名制約の ADD CONSTRAINT が同一ファイルにあれば違反。
    # 名前の後ろは識別子文字以外（または行末）を要求し、前方一致の誤検出を防ぐ。
    if printf '%s' "$content" | grep -qiE "ADD CONSTRAINT ${name}([^a-z0-9_]|\$)"; then
      echo "NG: $f: 制約 $name の ADD CONSTRAINT と VALIDATE CONSTRAINT が同一マイグレーションに同居しています。" >&2
      echo "    VALIDATE CONSTRAINT を後続の別マイグレーションへ分離してください（ADR-0052 / .claude/rules/migrations.md）。" >&2
      status=1
    fi
  done
done
exit "$status"
