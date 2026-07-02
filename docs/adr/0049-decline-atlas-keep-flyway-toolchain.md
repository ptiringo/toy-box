# 0049. Atlas（schema-as-code）を現時点では不採用とし Flyway 中心のスキーマツールチェーンを維持する

- Status: Accepted
- Date: 2026-07-03
- Deciders: ptiringo

## Context（背景・課題）

スキーマ管理は複数ツールで分担している: Flyway（`V*.sql` の逐次マイグレーション、Spring Boot autoconfig による起動時適用＋Testcontainers でのテスト適用）、squawk（マイグレーション安全性 lint、[ADR-0032](0032-sql-lint-squawk-sqlfluff.md)）、sqlfluff（SQL 書式）、tbls（ER 図・テーブル定義ドキュメント。#447 で実装済みだが draft PR #505 で保留中）。

[Atlas](https://atlasgo.io/)（"Terraform for databases"）は宣言的スキーマからのマイグレーション自動生成・`migrate lint`・ERD・ドリフト検出・Flyway ディレクトリの import を 1 つで担い、上記ツール群を統合しうる。本番 DB の PostgreSQL 化（#451 / [ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)）で前提条件（Atlas は H2 非対応）が解消したため、採否を評価した（#507）。

実地検証（atlas-community v1.2.0、mise アドホック実行＋`docker://postgres/17` dev-db）と一次情報調査の結果:

- **Flyway import・`migrate lint`・Mermaid ERD 生成・`migrate diff`（宣言的 desired state からのマイグレーション自動生成）は Community Edition（Apache 2.0）でログイン不要で動作**する。移行コスト自体は低い。
- ただし統合したい各役割に既存構成を上回る点がなかった:
  1. **適用エンジン**: Atlas に JVM ランタイムはなく、Boot 起動時適用（autoconfig＋Testcontainers＋docker-compose 自動配線）を CI/deploy 適用へ移す必要がある。運用を変える動機がない。
  2. **安全性 lint**: squawk と大幅に重複する上、実測では非 CONCURRENTLY の `CREATE INDEX` を指摘せず、ロック等の PostgreSQL 運用安全性はむしろ squawk が広くカバーする。
  3. **ERD / ドキュメント**: tbls（PR #505）が Markdown テーブル定義＋ER 図＋diff/lint まで揃うのに対し、Atlas 無料枠のローカル出力は Mermaid テキストどまり（schema docs 本体は Cloud 側）。
  4. **ドリフト検出**: Pro 専用（有料）で無料枠に存在しない。
  5. **authoring（`migrate diff`）**: 唯一の純増価値だが、Spring Data JDBC 用の schema provider は無く（公式は Hibernate/JPA のみ）、desired state を HCL/SQL で二重管理することになる。テーブル 5 個・手書き SQL＋squawk/sqlfluff で回る現規模では過剰。
- **ライセンスゲーティングの進行がリスク**: 配布は標準バイナリ（Atlas EULA）と Community Edition（Apache 2.0）の 2 系統で、v0.38（2025-10）に標準バイナリの `migrate lint` が Pro 専用化された実績がある。Community Edition は views/triggers 等非対応で公式 GitHub Actions / Terraform / K8s インテグレーションの対象外。無料枠から Pro への機能移動が段階的に進行しており、community 依存で組むと同じ動きの再来を引き受けることになる。
- Flyway 形式での diff 出力（部分採用の要）は動作するが、timestamp 版番号（`V20260702162805__...`）・undo ファイル・`atlas.sum` が Flyway ディレクトリへ混入し、既存の連番規約と揃わない摩擦がある。

評価の詳細（検証ログ・機能境界の出典）は #507 のコメントを参照。

## Decision（決定）

- Atlas は**現時点では採用しない**（全置換・部分採用とも）。
- スキーマツールチェーンは現行構成を維持する: 適用＝Flyway（Boot 起動時）、安全性 lint＝squawk、書式＝sqlfluff、ドキュメント＝tbls（#447 / PR #505 を再開してマージする）。
- 再評価のトリガを次のとおり定める: (a) コンテキスト・テーブル数の増加で手書きマイグレーションの作成・レビューが負担になったとき、(b) ドリフト検出の実需要が出たとき（Pro 課金の検討とセット）、(c) ORM を Hibernate/JPA 系へ移行したとき。

## Consequences（結果・影響）

- ツール追加なしで現行の開発フロー（起動時適用・pre-commit / CI の SQL チェック）がそのまま維持される。ADR-0047 / ADR-0048 で決定済みの FK・スキーマ分割の実装も従来どおり手書き Flyway マイグレーションで行う。
- マイグレーションの自動生成・ドリフト検出は引き続き持たない。スキーマと Flyway SQL の整合はレビューと契約テスト（Testcontainers）で担保し続ける。
- 再評価時の試行コストは低い（mise レジストリに `atlas-community` があり、`mise x atlas-community@latest` と `docker://postgres/17` の dev-db だけで #507 の検証を再現できる）。
- tbls の保留理由（「H2 脱却後に dbdoc 生成方式を見直す」）は本決定で解消し、PR #505 を進める。
