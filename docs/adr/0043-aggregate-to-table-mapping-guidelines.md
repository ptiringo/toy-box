# 0043. 集約⇔テーブルのマッピング指針（sealed/埋め込み VO の列設計）を定める

- Status: Accepted
- Date: 2026-06-30
- Deciders: Matsui

## Context（背景・課題）

#435 の Spring Data JDBC 移行で、集約内の sealed 型・nullable な埋め込み値オブジェクトをどうリレーショナルスキーマへ写すか、という設計判断が集約ごとに繰り返し現れた。PR #442（BloodHorse）の crit レビューで「フラット＋nullable でよいのか、設計意図は何か」という問いが出たのを受け、**方針を場当たり的に決めず指針として明文化する**ことになった（#443、レビューでの合意）。

これまでの暫定対応の実態（出所付き）:

- **1 集約 = 1 テーブル**を基本とし、1:1 子テーブルや JSON 列の迂回を避けてきた（Spring Data JDBC のマッピングが素直）。
- 相互排他な sealed や共在する nullable VO は、**判別子列＋各バリアントのフラット列**で表してきた。
  - `Origin`（sealed: Domestic/Imported）→ `origin_type` 判別子＋`sire_id`/`dam_id`・`origin_country`/`landing_date`（[0020](0020-sealed-origin-and-discriminated-origin-subobject.md)、PR #442）。
  - `FoalingOutcome`（sealed 9 バリアント）→ `outcome_type` 判別子＋`outcome_foaling_date`（PR #448）。
  - `ParentageDetermination`（sealed）→ `parentage_type` 判別子＋`dna_parentage_result`（[0038](0038-inspection-subdomain-aggregate.md)）。
  - `Covering`（nullable VO・複数列）→ 共在する 4 列フラット化（[0016](0016-not-covered-as-foaling-outcome-variant.md) / [0018](0018-uncovered-via-discriminated-single-create.md)）。
  - `BreedingRetirement`（nullable VO・2 列）→ 事由・発生日の 2 列フラット化（PR #441）。
- 「列定義が nullable」と「不正な組合せ（全 NULL・混在）を許す」は別問題として切り分け、相互排他・共在の不変条件は **CHECK 制約でスキーマ側にも強制**してきた（`chk_blood_horse_origin` / `chk_breeding_result_covering` / `chk_breeding_result_outcome_covering` / `chk_breeding_result_foaling_date` / `chk_horse_inspection_parentage`）。

しかし実態には**温度差**があった。上記のうち CHECK が付いているのは `origin` / `covering` / `outcome` / `parentage` で、**`breeding_registration.retirement` だけが CHECK 未設定**（共在する 2 列なのに「両方 NULL／両方 NOT NULL」の不変条件をスキーマ側で強制していない）。指針化にあたり、この温度差をどう扱うか（原則の確定と既存例外の解消時期）を決める必要がある。

また、上位の決定として [0041](0041-immutable-data-model-as-modeling-discipline.md) が**リソース/イベント分類**を設計語彙として導入済みで、「フラット＋判別子 vs 子テーブル」の使い分け基準にこの分類軸を据えることが期待されている。本 ADR はその具体適用（#443 のフォロー先）にあたる。

### 検討した代替案

- **使い分けの軸**: 「列数・バリアント数の定量しきい値で子テーブルへ切り替える」案も検討したが、しきい値は恣意的になりやすい。多重度（コレクションか否か）とイベント性（[0041](0041-immutable-data-model-as-modeling-discipline.md) の R/E）の 2 軸で線を引く方が、ドメインの意味と一致し説明可能性が高い。
- **CHECK の温度差**: 未設定の `retirement` に CHECK を**今すぐ遡及追加する**案も検討した。だが現状の DDL は H2(PostgreSQL 互換モード) と PostgreSQL の双方へ適用される（[0030](0030-jdbc-only-persistence-retire-inmemory.md)）一方、本番 PostgreSQL で安全な `ALTER TABLE ... ADD CONSTRAINT ... NOT VALID` ＋後続 `VALIDATE CONSTRAINT` は H2 が解釈できない。やむなく素の `ADD CONSTRAINT CHECK` を書くと、SQL 安全 lint（squawk、[0032](0032-sql-lint-squawk-sqlfluff.md)）の `constraint-missing-not-valid` / `require-timeout-settings` に触れ、これを通すには本番 PostgreSQL 向けの安全ルールを**暫定でしかない H2**（[0033](0033-defer-production-db-selection.md)）のために無効化する必要が生じる。守る対象（本番 PostgreSQL）を守る道具を、捨てる予定の H2 のために鈍らせるのは本末転倒であり、遡及追加は**見送る**（後述）。
- **JSON(JSONB) 列**: ネスト/可変構造の受け皿として検討したが、クエリ・CHECK・移行性（lint/差分）を損なうため原則採らない。

## Decision（決定）

集約をリレーショナルスキーマへ写すとき、以下を指針とする。

### 1. 基本: 1 集約 = 1 テーブル

[0027](0027-persistence-spring-data-jdbc.md) の既定を再確認する。1:1 子テーブルや JSON 列での迂回を避け、集約 1 つを 1 テーブルへ素直に写す。子テーブルを設けるのは「4. 子テーブル化の境界」に該当する場合に限る。

### 2. 単一の部分オブジェクトはフラット化する

集約内の sealed 型・nullable な埋め込み VO が**単一**（コレクションでない）なら、子テーブルを設けずフラット列で表す。

- **sealed 型**: 判別子列 `<名詞>_type`（値は対応 enum 名の文字列）＋各バリアント固有の属性列。非該当バリアントの列は nullable にせざるを得ない。
- **共在 nullable VO**: VO を構成する複数列をまとめてフラット化し、「全 NULL（不在）／全 NOT NULL（存在）」で在不在を表す。

### 3. 不変条件は CHECK 制約で必須強制する

相互排他（sealed の判別子に応じて該当列が NOT NULL・非該当列が NULL）や共在（VO 構成列が全 NULL か全 NOT NULL）の不変条件は、**例外なく CHECK 制約でスキーマ側にも強制する**。マッパーは常に整合した行を書くが、DB 単独でも不変条件が破られないようにする多層防御とする。

- CHECK 制約名は `chk_<table>_<rule>` とする。
- 既存の `origin` / `covering` / `outcome` / `parentage` はこの原則に既に従っている。**例外は `breeding_registration.retirement`（CHECK 未設定）の 1 件**で、これは既知の未整合として残す。今すぐ素の `ADD CONSTRAINT CHECK` で遡及追加すると、本番 PostgreSQL 向けの SQL 安全 lint を暫定 H2 のために緩める羽目になるため（「検討した代替案」参照）、**本番 PostgreSQL を採用するマイグレーション時点で `NOT VALID` ＋ `VALIDATE CONSTRAINT` を用いた安全な遡及を行う**（[0033](0033-defer-production-db-selection.md) / #451 と同期）。それまでは整合をドメイン VO（`BreedingRetirement`）の検証で担保する（現状どおり）。なお新規テーブルでは CREATE TABLE 内に CHECK をインライン定義するため、この問題は生じない。

### 4. 子テーブル化の境界: 多重度とイベント性

フラット化を原則とし、**次のいずれかに該当するときだけ**子テーブル（`@MappedCollection`）へ切り替える。

- **多重度**: 集約がコレクション（`List<...>`）を持つ。フラット列では表せない。
- **イベント性**: [0041](0041-immutable-data-model-as-modeling-discipline.md) のイベント（業務上起きた事実・日時 1 つ・INSERT-only）として記録する遷移。

R/E 分類軸での整理:

| | リソース（現在状態） | イベント（起きた事実・INSERT-only） |
|---|---|---|
| **単一** | 親行にフラット化＋UPDATE | （イベントは原則 INSERT-only なので子テーブル候補） |
| **コレクション** | 子テーブル | **INSERT-only 子テーブル** |

ロングタームイベント（始点終点を持つ長期プロセス。繁殖成績の covering→foaling 等）は「現在ステータスを持つ親行＋INSERT-only 詳細イベント子テーブル」で表す（[0041](0041-immutable-data-model-as-modeling-discipline.md)）。具体適用は #455-#457。

### 5. JSON(JSONB) 列は原則不採用

クエリ・CHECK 制約・移行性（SQL lint や差分レビュー）を損なうため、構造を JSON 列へ逃がさない。真にスキーマレスで高可変な構造（外部由来の任意ペイロード等）の要件が出たときに限り、別途 ADR で評価するエスケープハッチに留める。

### 6. 命名規約

- 判別子列: `<名詞>_type`（例: `origin_type` / `outcome_type` / `parentage_type`）。値は対応 enum 名の文字列。
- CHECK 制約: `chk_<table>_<rule>`。
- 子テーブル: 単数形のエンティティ／イベント名。

## Consequences（結果・影響）

- **得られるもの**: sealed/埋め込み VO の列設計を場当たりで決めず、レビューで本指針を参照できる。「相互排他・共在グループには一律 CHECK」という説明可能な原則に揃う。子テーブル化の判断が多重度・イベント性という意味的な軸に乗り、[0041](0041-immutable-data-model-as-modeling-discipline.md) の R/E 分類と直結する。
- **引き受けるもの**: 多バリアント sealed ではフラット列が増える（`FoalingOutcome` 等）。CHECK 制約のメンテナンスコストを負う。`breeding_registration.retirement` の CHECK 未設定が**既知の未整合として残る**（解消は本番 PostgreSQL 移行時。それまではドメイン VO の検証で担保）。SQL 安全 lint（squawk）の設定は暫定 H2 の都合で緩めない方針を維持する。
- **既存 ADR との関係**: [0020](0020-sealed-origin-and-discriminated-origin-subobject.md)（出自の判別子フラット化）を一般化した先行具体例として位置づけ、[0027](0027-persistence-spring-data-jdbc.md)（Row 分離・1 集約 1 テーブル）／[0041](0041-immutable-data-model-as-modeling-discipline.md)（R/E 分類・子テーブル化の軸）と両立する。
- **結論の置き場**: 「どう書くか」の要点は `.claude/rules/architecture.md`（`.kt` 編集時にロード）へ反映し、本 ADR は「なぜ」を保持する。
- **フォロー先**: コレクション／ロングタームイベントの具体設計は #455-#457 で本指針を適用しながら固める。更新系（リソースのみ UPDATE・イベントは INSERT-only）の実装制約は #424 に委ねる。
