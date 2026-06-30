# 0042. 外部公開 ID は当面 生 UUID 据え置きとし、不透明化・別 ID 体系の導入を遅延する

- Status: Accepted
- Date: 2026-06-30
- Deciders: Matsui

## Context（背景・課題）

PR #454（#325 繁殖成績年次集計）のレビューで、`GET /api/breedingResultSummaries?stallionId={uuid}` のように **生 UUID を外部公開 ID として晒している**点が指摘された。これは本リソースに限らず横断的で、現状は ID をパス・クエリ・リクエストボディ・レスポンスのすべてで生 UUID のまま露出している:

- **パス**: `@PathVariable id: UUID`（`JockeyController` / `BreedingResultController` 等）
- **クエリ**: `@RequestParam stallionId: UUID`（`BreedingResultSummaryController`）
- **リクエストボディ**: `sireId` / `damId` / `bloodHorseId` / `breedingRegistrationId` / `stallionRegistrationId`（いずれも UUID）
- **レスポンス**: `id: UUID` および各種 `〜Id: UUID`

> UUID を外部公開 ID として使うのはあまり良くなさそう。今すぐ直さなくてもよいが、方針を決めておきたい。（レビュー指摘）

懸念は主に 2 点:

1. **将来の内部表現の自由度**: クライアントがレスポンスの `id` を「UUID である」と当てにして使い始めると、後で ID のやり方（採番・表現）を変えたくなったときに HTTP 契約・生成クライアントを破壊的に壊すことになる。
2. **列挙耐性・情報漏洩**: ID 生成は時刻ベース UUID（[0005](0005-time-based-uuid-generation.md)）を採るため、UUID から生成時刻・生成順が推測できる。外部に晒すと作成順・件数が漏れうる。

### 検討した代替案

外部公開 ID の体系として、Issue #459 に挙がった候補を評価した。

- **生 UUID のまま（現状）**: 実装コストゼロ。ただし上記 1・2 の懸念を負う。
- **不透明 ID（base62 / ULID 等のエンコード、内部 UUID と分離）**: 外部表現から UUID の見た目を消し、内部 UUID と外部トークンを分離する変換層を adapter に置く。自由度・列挙耐性を「形」で担保できるが、相互変換層の追加・全エンドポイントの ID 型差し替え・テスト更新という実装コストが大きい。
- **リソース名（[AIP-122](https://google.aip.dev/122) 準拠の relative resource name `bloodHorses/{id}` 形式）**: レスポンスの `id`→`name`、参照の `〜Id`→resource name 文字列（[AIP-124](https://google.aip.dev/124)）への wire 契約リネームを伴う。AIP 純度は最も高いが、横断的な契約変更コストが大きい。

ここで重要なのは、懸念 1（将来の自由度）を支える本体は **ID 表現そのものの差し替え**ではなく **「クライアントは ID を opaque として扱い、構造を解釈・推測してはならない」という契約上の約束**（AIP-122 が ID/リソース名に課す原則）だという点。この約束さえ規約として敷けば、外部表現が生 UUID のままでも「中身は内部詳細であり依存してはならない」と宣言でき、将来の差し替え自由度は確保できる。不透明エンコードや resource name 形式は、その約束を*見た目で強制する*オプションにすぎない。

### 現状の実態

- **実データ・実ユーザ・実クライアントがまだ存在しない探索 sandbox**である（[0033](0033-defer-production-db-selection.md) と同じ前提）。ID を当てにして壊れるクライアントは現時点で存在しない。
- 列挙・情報漏洩の懸念も、実 PII・実トラフィックが無い段階では脅威として薄い。
- 不透明化・resource name 形式が負う対価（変換層・契約リネーム・テスト更新）は、いずれも不可逆寄りで、駆動要因（実クライアント・実運用）が無い時点で先払いすると陳腐化・空コストになりうる。

## Decision（決定）

**外部公開 ID は当面 生 UUID 据え置きとし、これを意図的な決定とする。** 不透明 ID エンコード・AIP resource name 形式・別採番体系の導入は、駆動要因が現れた時点まで遅延する。具体的には:

- パス・クエリ・リクエストボディ・レスポンスの ID は現状どおり生 UUID で露出する（コード変更なし）。
- ただし規約として **「外部公開 ID は不透明な識別子（opaque identifier）であり、クライアントはその構造を解釈・推測してはならず、受け取った値をそのまま使うこと」** を宣言する（AIP-122 の原則に沿う）。これにより、将来 ID 表現を差し替えても契約違反としないための足場を今のうちに敷く。
- この方針を `.claude/rules/api-design.md` に一文として追記する。

### 再評価トリガ（いずれかを満たした時点で外部 ID 体系を再検討する）

1. **実クライアント・実運用が現れる**（生成クライアント配布・外部公開など、ID を当てにする利用者が生まれる）。
2. **列挙耐性・情報漏洩が実要件になる**（実 PII・推測されたくない作成順／件数を扱う）。
3. **AIP resource name 形式への移行を別途決める**（リソース指向を URL/参照の両面で貫きたくなる）。

### 再評価時の出発点

- 自由度・列挙耐性を「形」で担保したい → **不透明 ID（base62 / ULID 等）** を adapter 変換層で導入。
- AIP 純度・リソース指向の一貫性を優先 → **relative resource name（`bloodHorses/{id}`）形式**（[AIP-122](https://google.aip.dev/122) / [AIP-124](https://google.aip.dev/124)）。
- いずれも「外部 ID は opaque」の規約を先に敷いてあるため、移行はクライアント契約上の約束違反にはならない。

## Consequences（結果・影響）

- **良くなること**: 実需要が無い段階で不可逆寄りのコスト（変換層・契約リネーム・テスト更新）を負わない。「外部 ID は opaque」の規約を先に敷くことで、将来の差し替え自由度（懸念 1）はコードを触らずに確保できる。#459 を「なんとなく生 UUID」の未決状態から「意図的に生 UUID 据え置き」の決定済み状態へ移し、放置と保留を区別できる。
- **引き受けること**: 生 UUID の露出による列挙耐性・時刻漏洩（[0005](0005-time-based-uuid-generation.md)）の懸念は当面残す（実 PII・実トラフィックが無いため許容）。規約の「opaque として扱え」は宣言にとどまり、機械的・形式的な強制はしない（再評価トリガまで運用と規約で担保）。
- `.claude/rules/api-design.md` に「外部公開 ID は opaque・当面生 UUID 据え置き」の一文を追記する。関連: ID 生成の [0005](0005-time-based-uuid-generation.md)、REST 命名規約の [0012](0012-rest-naming-convention.md)、リソース表現の [0008](0008-uniform-resource-representation-response.md)、同型の「実需要まで遅延」判断である [0033](0033-defer-production-db-selection.md)。
