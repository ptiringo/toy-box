---
paths:
  - "src/main/resources/db/migration/**"
---

# Flyway マイグレーション規約

## VALIDATE CONSTRAINT は別マイグレーションへ分離する（ADR-0052）

既存行のあるテーブルへ VALIDATE 対象制約（CHECK / FK）を追加するときは、**必ず 2 ファイルに分ける**:

- `V<n>__add_xxx_check.sql` — `ADD CONSTRAINT ... NOT VALID` まで
- `V<n+1>__validate_xxx_check.sql` — `VALIDATE CONSTRAINT` のみ（他の DDL を同居させない）

Flyway は各マイグレーションを 1 トランザクションで適用するため、同一ファイルに同居させると
`ADD CONSTRAINT` のロック（CHECK は ACCESS EXCLUSIVE、FK は SHARE ROW EXCLUSIVE）がコミットまで残り、
`VALIDATE` の SHARE UPDATE EXCLUSIVE への格下げが効かない（無停止効果が出ない）。
テーブル規模によらず常に分離する（閾値なし）。

- 同居は `scripts/check-migration-validate-separation.sh` が検出し、
  lefthook pre-commit / CI（`sql-check.yml`）で fail する。
- **V6 / V8 / V9 は同居したままの既知例外**（規約制定前に本番適用済み。コメントのみの変更でも
  Flyway チェックサムが変わるため編集禁止）。**新しいマイグレーションの手本にしないこと**。
  V6/V8/V9 内の「書き込みを止めない」コメントは同一トランザクション内では事実誤認（ADR-0052）。
- 新規テーブルの `CREATE TABLE` インライン CHECK はこの規約の対象外。

## timeout は各ファイルに書く

squawk（`require-timeout-settings`）に従い、既存テーブルを変更するマイグレーションの冒頭で
`SET LOCAL lock_timeout = '5s';` / `SET LOCAL statement_timeout = '5min';` を設定する
（SET LOCAL はそのマイグレーションのトランザクション内に閉じる）。

## 適用済みマイグレーションは編集しない

Flyway のチェックサムはコメントを含むファイル全体から計算される。適用済みファイルの変更は
`validate-on-migrate` を落とすため、修正が要るときは新しいマイグレーションを足す。
