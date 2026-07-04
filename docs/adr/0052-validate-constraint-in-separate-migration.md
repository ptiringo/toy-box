# 0052. VALIDATE CONSTRAINT は後続の別マイグレーションへ分離する

- Status: Accepted
- Date: 2026-07-04
- Deciders: Matsui

## Context（背景・課題）

既存行のあるテーブルへ CHECK 制約を遡及追加した V6（#522）・V8（#455）・V9（#531、version NOT NULL 遡及付与の中間 CHECK）は、`ADD CONSTRAINT ... NOT VALID` → `VALIDATE CONSTRAINT` の 2 段階を**同一マイグレーションファイル内**で実行し、コメントで「書き込みを止めない」と謳っていた。

しかし PR #537 の最終レビューで指摘されたとおり、Flyway は各マイグレーションを 1 トランザクションで適用するため、`ADD CONSTRAINT` が取得した ACCESS EXCLUSIVE ロックはコミットまで保持される。同一トランザクション内の `VALIDATE CONSTRAINT` は SHARE UPDATE EXCLUSIVE へ「格下げ」されず、フルスキャン検証中もテーブルは排他ロックされたまま。つまり同一ファイル内の 2 段階は無停止効果を発揮していない（見かけ倒し）。V6/V8/V9 のコメント「VALIDATE CONSTRAINT で既存行を後追い検証する（SHARE UPDATE EXCLUSIVE・書き込みを止めない）」は**同一トランザクション内では事実誤認**だった。

現状は `SET LOCAL lock_timeout = '5s'` と行数の少なさで実害はほぼゼロだが、「安全パターンを踏んだ」という誤った安心がテーブル成長後の将来マイグレーションへ複製され続けるリスクがある（#539）。

## Decision（決定）

**既存テーブルへの VALIDATE 対象制約（CHECK / FK）の追加は、常に 2 マイグレーションへ分離する（閾値なし）:**

- `V<n>__add_xxx_check.sql` — `ADD CONSTRAINT ... NOT VALID` まで
- `V<n+1>__validate_xxx_check.sql` — `VALIDATE CONSTRAINT` のみ

分離すれば Flyway が連続適用しても別トランザクションになるため、`ADD ... NOT VALID` のコミットで排他ロックが解け、後続の `VALIDATE` は SHARE UPDATE EXCLUSIVE のみで走る（本来の無停止効果が出る）。

- **閾値は設けない**: テーブル規模による分岐は人の判断と allowlist 運用を呼び込み、誤った安心の複製リスクが残る。一律ルールなら機械チェック可能。
- **VALIDATE 専用マイグレーションには他の DDL を同居させない**: そのトランザクションのロックを SHARE UPDATE EXCLUSIVE のみに保つため。
- **対象外**: 新規テーブルの `CREATE TABLE` インライン CHECK（既存行が無くロック問題が生じない）。
- **機械チェック**: `scripts/check-migration-validate-separation.sh` が同一ファイル内の同名制約 `ADD CONSTRAINT` と `VALIDATE CONSTRAINT` の同居を検出し、lefthook pre-commit と CI（`sql-check.yml`）の両方で fail させる。squawk に該当ルールは無いため自前実装（`constraint-missing-not-valid` は NOT VALID の欠落を検出するのみで、同居は検出しない）。
- **V6/V8/V9 は編集しない**: Flyway のチェックサムはコメントを含むファイル全体から計算されるため、コメントのみの更正でも適用済みの本番 DB（Prisma Postgres）で validate-on-migrate が落ちる。`flyway repair` の本番オペを払う価値はなく、ファイルは触らず本 ADR に事実誤認を記録して訴求先とする。機械チェックでは既知例外（baseline）として除外する。
- `.squawk.toml` は変更不要（`VALIDATE` 単体のファイルは `constraint-missing-not-valid` に触れないことを確認済み）。
- 作業時ガイダンスは `.claude/rules/migrations.md`（`src/main/resources/db/migration/**` に path-scope）に置く。

## Alternatives（検討した代替案）

- **閾値付き分離（現規模なら同一ファイル可）**: 機械チェックがテーブル行数を知れず allowlist 運用になる。「いつ閾値を越えたか」の判断も人任せで、#539 が懸念する誤った安心の複製が残る。不採用。
- **分離しない（コメント更正のみ）**: `lock_timeout = '5s'` が実害を抑えるが、NOT VALID パターン自体が形骸化し、テーブル成長後に危ない前例として残る。不採用。
- **V6/V8/V9 を編集して `flyway repair`**: ファイル単体で読んでも正しい状態になるが、コメント更正のためだけに本番オペを 1 回払うことになる。不採用。
- **Bytebase の導入**: DB DevSecOps プラットフォーム。SQL Review（200+ ルール）を持つが常駐サーバー必須で solo sandbox には運用が不釣り合い、`ADD` と `VALIDATE` の同居を検出するルールも持たない。squawk との重複が大きく不採用（[0049](0049-decline-atlas-keep-flyway-toolchain.md) の Flyway ツールチェーン維持判断とも整合）。

## Consequences（帰結）

- 今後の遡及制約追加はマイグレーションファイルが 1 枚増えるが、規模判断なしの一律ルールとして機械的に強制される。
- V6/V8/V9 は「同居したままの既知例外」として残る（実害は lock_timeout と少行数で抑制）。将来の読み手は本 ADR と機械チェックで正しいパターンへ誘導される。
- CHECK 多層防御の原則（[0043](0043-aggregate-to-table-mapping-guidelines.md)）は変わらない。本 ADR はその適用手順（遡及追加時のファイル分割）を定めるもの。
