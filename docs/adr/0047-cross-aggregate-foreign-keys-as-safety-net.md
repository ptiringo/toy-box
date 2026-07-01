# 0047. 集約間参照に外部キー制約を安全網として張る（DB 参照整合性の多層防御）

- Status: Accepted
- Date: 2026-07-02
- Deciders: Matsui

## Context（背景・課題）

tbls 導入（#447）の crit レビューで、生成された ER 図に集約テーブル間の関連線が出ないのは **DB に外部キー(FK)制約を張っていない**ためと指摘された（#508）。現状、集約間の参照はすべて**アプリ側の UUID 列**で表し、FK を張っていない。

参照関係（いずれも現状 FK なし）:

- `breeding_registration.registered_horse_id` → `blood_horse`
- `blood_horse.inspection_id` → `horse_inspection`（別サブドメイン集約、[0038](0038-inspection-subdomain-aggregate.md)）
- `blood_horse.sire_id` / `dam_id` → `blood_horse`（父・母。自己参照）
- `breeding_result.breeding_registration_id` → `breeding_registration`
- `breeding_result.covering_stallion_id` → `blood_horse`（種付種牡馬）

論点は「**DB レベルの参照整合性を担保すべきか**」。crit の関心は主に整合性担保（ER 図の可視化は副次）にある。

### 技術的成立性

現行ドメインモデルでは、これら参照先はすべて**自 DB の登録済み行**を指す（`registerInStudBook` は解決済みの `sire`/`dam: BloodHorse` を引数で要求し、`recordCovering` は種牡馬の `BreedingRegistration` を要求する）。よって FK は 6 本すべてで技術的に成立する。すなわち本 ADR の争点は「張れるか」ではなく、「**集約独立性を一部譲ってでも DB 安全網を得るか**」というトレードオフである。

### 検討した代替案

- **案A（FK を張らない・現状維持）**: 整合性はドメイン層（[0022](0022-domain-service-repository-for-set-invariants.md) の読み取り引き当て）＋ #483 のトランザクション境界で担保する。[0027](0027-persistence-spring-data-jdbc.md) / [0030](0030-jdbc-only-persistence-retire-inmemory.md) / [0043](0043-aggregate-to-table-mapping-guidelines.md) の「集約＝独立境界・ID 参照」路線に忠実だが、DB 単独の安全網が無く、バグで dangling reference を書きうる。
- **案B（FK を安全網として張る・採用）**: [0043](0043-aggregate-to-table-mapping-guidelines.md) が既に採る「マッパーは整合した行を書くが、DB 単独でも不変条件を破らせない多層防御（CHECK 制約）」の**参照整合性版**として位置づける。DB 整合性が本丸という判断（crit の関心）に沿う。
- **FK を一部だけ張る案**も検討したが、案B の根拠（DB 単独で整合性を保証）が中途半端になるため、成立する全参照に張る。

### FK と #483 の役割分担

FK は「**dangling reference**（存在しない親を指す子。例: 実在しない `blood_horse` を指す `breeding_registration`）」を防ぐが、「**orphan parent**（子に参照されない孤児親。例: 審査だけ作られ軽種馬が未作成）」は防げない。後者は #483 のトランザクション境界の担当であり、両者は**別々の失敗モードを補完**する。

## Decision（決定）

集約間参照 **6 本すべてに FK 制約を張り**、参照元 **6 列にインデックス**を張る（多層防御の参照整合性版）。

- 対象列: `registered_horse_id` / `inspection_id` / `sire_id` / `dam_id` / `breeding_registration_id` / `covering_stallion_id`。
- nullable 列（`sire_id` / `dam_id` / `covering_stallion_id`）は NULL 時に FK チェックが走らないため、輸入馬に父母が無い・種付なしで種牡馬 NULL 等を自然に表現できる。
- **FK はコンテキスト内に限定する**。現状 6 本はすべて studbook コンテキスト内で閉じ、コンテキスト間（クロススキーマ、[0048](0048-per-context-db-schema-namespaces.md)）参照には FK を張らない＝コンテキスト間は ID 参照のまま。ArchUnit のコンテキスト分離と一直線に揃える。
- 削除挙動は既定（RESTRICT / NO ACTION）。イミュータブル・追記のみ（[0009](0009-immutable-aggregates.md)）のため CASCADE は使わない。
- FK 列にインデックスを張り、#447 の tbls `requireForeignKeyIndex` lint を有効化できるようにする（#447 マージ後）。

### 実装タイミングと方式

- 決定は本 ADR で確定し、**実装は #451（本番ランタイムを実 DB へ配線）の残る H2 cutover ステップに委ねる**。#451 は #511 で env 差し替え可能化・ローカル compose PostgreSQL まで landing 済みだが、H2 は Docker-less フォールバックとして残置しており、全面廃止（cutover）は #451 の残作業（`build.gradle.kts` に明記）。
- 既存テーブル（V1–V5）への FK 追加は `ALTER TABLE ADD CONSTRAINT` となり、squawk の `constraint-missing-not-valid`（[0032](0032-sql-lint-squawk-sqlfluff.md)）に触れる。[0043](0043-aggregate-to-table-mapping-guidelines.md) が retirement CHECK で選んだのと同じ理由により、**暫定 H2 のために squawk を緩めず、H2 cutover 後（PostgreSQL 専用）に `ADD CONSTRAINT ... NOT VALID` ＋ `VALIDATE CONSTRAINT` で安全遡及**する。
- cutover まで現状維持（FK なし）。cutover の協調マイグレーションで、スキーマ移設（[0048](0048-per-context-db-schema-namespaces.md)）と FK / インデックス追加を**一括実施**する。cutover 前に新設される表も public に置き、cutover で一緒に移す（移行期のクロススキーマ FK の混乱を避ける）。

## Consequences（結果・影響）

- **得られるもの**: DB 単独でも dangling reference を防ぐ多層防御。[0043](0043-aggregate-to-table-mapping-guidelines.md) の CHECK 制約と同じ「DB がマッパーと独立に不変条件を保証する」思想で一貫する。#447 の ER 図に関連線が出て `requireForeignKeyIndex` lint が活きる。
- **引き受けるもの（集約ライフサイクル結合）**:
  - 書き込み順序が硬直化する（親を先に commit）。#483 のトランザクション境界設計と整合を取る。
  - 削除・アーカイブ順序が FK グラフに縛られる。契約テストの `deleteAll` 順序（子→親）に影響（#440）。
  - `inspection`（別サブドメイン集約、[0038](0038-inspection-subdomain-aggregate.md)）を将来別 DB / 別サービスへ切り出す際は FK を落とす必要がある＝独立性を一部譲る。
  - 将来「外国産駒の父（JAIRS 未登録）」を非登録 ID で持つ拡張時は、当該 FK の見直しが要る。
- **案A（DDD 正統）との分岐点**: CHECK 制約（[0043](0043-aggregate-to-table-mapping-guidelines.md)）と異なり、FK は**行をまたぐ＝集約をまたぐ結合**を持ち込む。本 ADR は「DB 整合性が本丸」という判断に基づき案B を採る。
- **関連**: [0048](0048-per-context-db-schema-namespaces.md)（コンテキスト別スキーマ。FK をコンテキスト内に閉じる・相互補強）、[0022](0022-domain-service-repository-for-set-invariants.md)（読み取り引き当て）、[0027](0027-persistence-spring-data-jdbc.md) / [0030](0030-jdbc-only-persistence-retire-inmemory.md)（集約＝永続化境界）、[0038](0038-inspection-subdomain-aggregate.md)（審査集約）、[0043](0043-aggregate-to-table-mapping-guidelines.md)（多層防御・CHECK）、[0032](0032-sql-lint-squawk-sqlfluff.md)（squawk）、#483 / #440 / #451 / #447。
