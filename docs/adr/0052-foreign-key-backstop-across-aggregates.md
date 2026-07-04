# 0052. 集約間の ID 参照に外部キー制約を backstop として張る

- Status: Accepted
- Date: 2026-07-04
- Deciders: ptiringo, Claude Code

## Context（背景・課題）

テーブル間の参照はすべてアプリ側の UUID 列（集約間の ID 参照）で表現しており、DB の外部キー制約を張っていなかった（#508。発端は #447 / [ADR-0045](0045-tbls-db-schema-docs.md) の tbls 導入時、ER 図に関連が一切描かれないという crit レビュー指摘）。

FK を張らない立場には根拠があった。DDD では集約が整合性境界であり、集約間は ID 参照で疎結合に保つのが定石（[ADR-0027](0027-persistence-spring-data-jdbc.md) / [ADR-0030](0030-jdbc-only-persistence-retire-inmemory.md) の「集約 = 永続化境界」）。また V2〜V5 作成当時はランタイムが H2（PostgreSQL 互換モード）との両対応で、PostgreSQL 固有の段階的制約追加（`NOT VALID`）が使えなかった。

一方でこのプロジェクトは「アプリのマッパーは常に整合した行を書くが、DB 単独でも不変条件が破られないようにする」という多層防御をすでに実践している。sealed 型・値オブジェクトの不変条件は CHECK 制約（[ADR-0043](0043-aggregate-to-table-mapping-guidelines.md)）、集合制約（種牡馬×種付年の一回性）は UNIQUE 制約（#532/#540）でスキーマ側にも強制しており、参照整合性だけ DB の関与から除外する積極的な理由がない。H2 全面脱却（#451 / [ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)）で構文上の制約も消えた。

検討した代替案:

- **FK を張らず現状維持（＋ tbls の手動 `relations:` 定義で ER 図だけ得る）**: DDD の定石に忠実だが、主目的である参照整合性の担保を満たさない。手動 relations は実スキーマと乖離しうる二重管理になるため採らない。
- **必須参照（NOT NULL 列）のみ FK を張る折衷**: nullable な参照（`sire_id` / `dam_id` / `covering_stallion_id`）でも「非 NULL なら実在する行を指す」は守りたい不変条件であり、除外する理由がない。中途半端に済ませると ER 図・lint も部分的になるため採らない。

## Decision（決定）

集約間の ID 参照列すべてに外部キー制約を **backstop** として張る。

- **役割分担**: 参照整合性の一次担保はこれまで通りドメイン層が担う（親の引き当て検証は [ADR-0021](0021-parent-not-found-unprocessable-entity.md) / [ADR-0022](0022-domain-service-repository-for-set-invariants.md)）。FK はアプリ検証をすり抜けた壊れた参照を DB が最後に止める多層防御であり、業務フローの検証を FK 違反例外に頼らない。
- **DDD との整理**: FK は O/R マッピングにも save 単位にも影響せず、集約間 ID 参照・集約単位の保存というアプリの姿はそのまま。「集約 = 永続化境界」（ADR-0027/0030）と矛盾しない。集約**内**の関係はフラット化（ADR-0043）のままで、FK の対象は集約**間**参照のみ。
- **参照アクション**: `ON DELETE` / `ON UPDATE` は既定（NO ACTION）。削除ユースケースが存在しないため削除セマンティクスは設計せず、必要が生じたら再訪する。`DEFERRABLE` も使わない（ユースケースは親→子の順で保存する）。
- **既存テーブルへの追加手順**: V6/V8 の前例に従い、`SET LOCAL` タイムアウト＋ `ADD CONSTRAINT ... NOT VALID` → `VALIDATE CONSTRAINT` の 2 段で行う（#539 の規約検討が確定したらそちらに従う）。
- **FK 列の index**: PostgreSQL は FK 列に index を自動作成しないため、FK 追加とセットで参照列の index を張る（`.tbls.yml` の `requireForeignKeyIndex` lint で強制）。既存 index（UNIQUE 制約由来を含む）の先頭列が FK 列を担保する場合は追加しない。
- **今後の規約**: 新しい集約間参照列を追加するときは FK と index を同時に張る。

対象エッジ（初回導入時点）: `breeding_registration.registered_horse_id` → `blood_horse`、`blood_horse.inspection_id` → `horse_inspection`、`blood_horse.sire_id` / `dam_id` → `blood_horse`（自己参照）、`breeding_result.breeding_registration_id` → `breeding_registration`、`breeding_result.covering_stallion_id` → `blood_horse`、`covering_report.stallion_breeding_registration_id` → `breeding_registration`。

## Consequences（結果・影響）

- 壊れた参照（存在しない親を指す行）がどの経路からも DB に入らなくなり、集計・逆リンク系の機能（#456/#457 など）が参照整合を前提にできる。
- tbls の ER 図に関連が描かれ、`unrelatedTable` / `duplicateRelations` / `requireForeignKeyIndex` lint が実効化される（`.tbls.yml` の無効化理由「FK が無いため」が解消）。
- 契約テストは親行の seeding が必要になる（ランダム UUID の親 ID では insert できない）。テーブルを跨ぐクリーンアップは子→親の順序で行う。フィクスチャがやや重くなるのは backstop の対価として引き受ける。
- FK 違反がアプリまで届いた場合は整合性バグの発見であり、`DataIntegrityViolationException`（500 相当）で表面化してよい（業務上の検証失敗はドメイン層が先に 4xx で返す）。
- insert は親→子の順序制約を受ける。ユースケースが単一 Tx（[ADR-0051](0051-transactional-use-case-boundary.md)）で親→子の順に保存する現行構造では追加コストなし。
