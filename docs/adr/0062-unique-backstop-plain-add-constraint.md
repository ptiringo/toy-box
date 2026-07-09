# 0062. 既存テーブルへの UNIQUE backstop は素の ADD CONSTRAINT で行う

- Status: Accepted
- Date: 2026-07-09
- Deciders: Matsui

## Context（背景・課題）

ドメインサービスは既存レコード集合を読み取って一意性を検証する（ADR-0022）。しかし READ COMMITTED 下の read-then-insert には並行競合の窓があり（#532）、検証をすり抜けた敗者の insert が重複データを永続化しうる。これを DB の UNIQUE 制約で塞ぐ（#540 の `covering_report`、#544 の `blood_horse.name` と `breeding_result`）。

`covering_report` は新規テーブルだったため `CREATE TABLE` にインラインで UNIQUE を書けた。既存テーブルへ後から追加する #544 では、そうはいかない。

- PostgreSQL の `ALTER TABLE ... ADD CONSTRAINT ... UNIQUE` は索引を構築するあいだ ACCESS EXCLUSIVE ロックを取り、読み書きを止める。
- squawk（ADR-0032）は `disallowed-unique-constraint` でこれを警告し、`CREATE INDEX CONCURRENTLY` ＋ `ADD CONSTRAINT ... USING INDEX` を勧める。
- 同時に `constraint-missing-not-valid` も発火するが、**PostgreSQL は `NOT VALID` を CHECK と FOREIGN KEY にしか許さず UNIQUE には使えない**ため、この助言はそもそも従えない。

## Decision（決定）

既存テーブルへの UNIQUE backstop は、トランザクション内の**素の `ALTER TABLE ... ADD CONSTRAINT ... UNIQUE`** で追加する。squawk の上記 2 ルールは、**当該文に限って**抑止する。

- 抑止は `-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid` を警告が紐づく `ADD CONSTRAINT` 行の直前に置いて行う（カンマ区切り・空白なし）。
- **`-- squawk-ignore-file` は使わない**。ファイル全体の全ルールを無効化し、将来の警告まで見えなくするため。
- ADR-0052 の「VALIDATE CONSTRAINT を別マイグレーションへ分離する」規約は、UNIQUE に `NOT VALID` が存在しない以上、対象外である。
- nullable 列への UNIQUE は素の `UNIQUE (col)` でよい。PostgreSQL は UNIQUE 制約において NULL 同士を衝突とみなさないため、partial index は不要（`blood_horse.name` は未命名の馬で NULL）。

**再評価トリガ**: 対象テーブルが「索引構築のあいだ書き込みを止めても許容できる」規模を超えたとき。そのときは `CREATE UNIQUE INDEX CONCURRENTLY` ＋ `ADD CONSTRAINT ... USING INDEX` へ移し、非トランザクション実行に伴う失敗時の復旧手順（INVALID index の `DROP INDEX` と Flyway の repair）を用意する。

なお ADR-0043 が前提としていた「DDL は H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用可能な構文に保つ」という制約は、**#451（H2 全面脱却）で失効した**。以後の DDL は PostgreSQL 専用構文でよい。ADR-0043 は決定時点の文脈を残す記録として改訂しない。

## Alternatives（検討した代替案）

- **`CREATE UNIQUE INDEX CONCURRENTLY` のみ**: lint を完全に通り、真に無停止。だが Flyway の PostgreSQL パーサーが `CREATE INDEX CONCURRENTLY` を非トランザクションと判定するため、失敗すると INVALID index が残りマイグレーションが failed 状態のまま残る。本番は Cloud Run の**起動時に Flyway が migrate する**構成なので、この状態はデプロイを詰まらせ、手動の SQL 実行による復旧を要する。named constraint ではなく index になるため `covering_report` と非対称にもなる。
- **`CREATE UNIQUE INDEX CONCURRENTLY` ＋ `ADD CONSTRAINT ... USING INDEX`（2 ファイル）**: 上に加えて named constraint の対称性は保てるが、非トランザクションの復旧リスクはそのまま残り、マイグレーションが 2 枚に増える。
- **`.squawk.toml` の `excluded_rules` へ追加**: プロジェクト全体でルールが無効になり、将来の本当に危険な UNIQUE 追加を見逃す。

ADR-0052 は「lint を黙らせず実際に無停止にする」と定めたが、あれは VALIDATE の分離コストがゼロだったからである。今回の CONCURRENTLY は実運用リスクを伴うため、同じ結論にはならない。現行のデータ量（数十行規模）では ACCESS EXCLUSIVE は一瞬で終わり、回復可能性（トランザクション内・失敗時に自動 rollback）を優先する価値のほうが大きい。

## Consequences（帰結）

- **得るもの**: ドメインサービスの一意性検証をすり抜けた並行 insert を DB が最後に止める多層防御。マイグレーションは原子的で、失敗しても自動 rollback される。squawk の抑止は文単位・2 ルール限定に留まり、他の警告は生きたまま。
- **引き受けるもの**: 索引構築のあいだ ACCESS EXCLUSIVE ロックを取る（現行規模では一瞬）。squawk の警告を意図的に抑止した箇所が 2 つ増え、テーブル成長時に CONCURRENTLY へ移す宿題を負う。
- UNIQUE 違反（`DuplicateKeyException`）は 409 へ写像せず 500 のままとする（#532 の決定を継承）。通常経路ではドメインサービスが 409 を返す。再評価トリガは「実クライアントで競合時の 500 が問題になったとき」。
