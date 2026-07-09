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

## 既存テーブルへの UNIQUE 追加（ADR-0062）

既存テーブルへ UNIQUE backstop を張るときは、素の `ALTER TABLE ... ADD CONSTRAINT ... UNIQUE` を使う
（索引構築のあいだ ACCESS EXCLUSIVE を取るが、現行のデータ量では一瞬）。`CREATE INDEX CONCURRENTLY` は
非トランザクション実行となり、失敗時に INVALID index を残して起動時 migrate を詰まらせるため採らない。

- **`NOT VALID` は使えない**。PostgreSQL は CHECK と FOREIGN KEY にしか許さないため、ADR-0052 の
  VALIDATE 分離規約はこのケースの対象外。
- squawk の `disallowed-unique-constraint` / `constraint-missing-not-valid` は、**警告が紐づく
  `ADD CONSTRAINT` 行の直前**に抑止コメントを置いて黙らせる。ルールは**カンマ区切り・空白なし**で並べる。

  ```sql
  ALTER TABLE studbook.blood_horse
  -- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
  ADD CONSTRAINT uq_blood_horse_name UNIQUE (name);
  ```

  `ALTER TABLE` 行の上に置いても効かない。空白区切り・コロン付き（`-- squawk-ignore: a, b`）も効かない。
- **`-- squawk-ignore-file` は使わない**（そのファイルの全ルールを無効化し、将来の警告まで見えなくする）。
- nullable 列は素の `UNIQUE (col)` でよい（PostgreSQL は NULL 同士を衝突とみなさない）。partial index は不要。
- 命名は `uq_<table>_<columns>`。

## DDL は PostgreSQL 専用構文でよい

H2 は #451 で全面脱却済み。ローカル・CI・テスト・本番のすべてが PostgreSQL であり、DDL を H2 互換に
保つ必要はない（`SET LOCAL` / `ALTER TABLE ... SET SCHEMA` 等を自由に使ってよい）。

## timeout は各ファイルに書く

squawk（`require-timeout-settings`）に従い、既存テーブルを変更するマイグレーションの冒頭で
`SET LOCAL lock_timeout = '5s';` / `SET LOCAL statement_timeout = '5min';` を設定する
（SET LOCAL はそのマイグレーションのトランザクション内に閉じる）。

## 適用済みマイグレーションは編集しない

Flyway のチェックサムはコメントを含むファイル全体から計算される。適用済みファイルの変更は
`validate-on-migrate` を落とすため、修正が要るときは新しいマイグレーションを足す。
