# 0020. 出自を sealed Origin に統合し、リソース表現に discriminated 部分オブジェクトを許す

- Status: Accepted
- Date: 2026-06-23
- Deciders: ptiringo

## Context（背景・課題）

[#267](https://github.com/ptiringo/toy-box/issues/267)（PR #358）で父母不明の輸入馬の血統登録経路を追加した際、`BloodHorse` の出自を **nullable フィールド4つ平置き**で表した（`sireId` / `damId` / `originCountry` / `landingDate`）。これは「内国産（父母あり）」と「輸入（原産国・揚陸日あり）」という**相互排他の和型**を平置きで近似したもので、全 null・混在（父母 ID と原産国を同時に持つ）といった無効状態を型で防げていなかった。#267 は first cut として平置きで通し、硬化を本 issue（[#362](https://github.com/ptiringo/toy-box/issues/362)）に切り出した。

レスポンス `BloodHorseResponse` も同じ4フィールドを nullable 平置きで返しており、クライアントから見ても「どの組み合わせが有効か」が型に現れていなかった。

論点は2つに分かれる:

1. **ドメイン**: 相互排他をどう型で強制するか。
2. **HTTP 契約**: 単一リソース表現を返す方針（[ADR-0008](0008-uniform-resource-representation-response.md)）と、相互排他な出自の表現をどう両立させるか。

検討した代替案:

- **案A: 平置き nullable を維持**。変更は最小だが、無効状態を型で防げない問題がそのまま残る。集約の不変条件（出自はどちらか一方）をコードコメントとレビューに頼ることになる。
- **案B: リソース全体を `oneOf`（内国産レスポンス型／輸入レスポンス型）に割る**。出自以外の共通項（登録番号・性・毛色・品種・生年月日・生産者・マイクロチップ・馬名）まで2つの表現に分裂し、ADR-0008 の「同一リソースは単一の表現を返す」と正面衝突する。クライアントは共通項を読むのに型判別を強いられる。

## Decision（決定）

**ドメインの出自を sealed `Origin` 値オブジェクトに統合し、HTTP 契約では相互排他な部分だけを discriminated な入れ子オブジェクト `origin` として返す。**

### ドメイン

- `domain.horseracing.model.horse.bloodhorse` に sealed interface `Origin`（`@ValueObject`）を新設し、`Origin.Domestic(sireId, damId)` と `Origin.Imported(originCountry, landingDate)` の2バリアントで相互排他を表す。
- `BloodHorse` の4 nullable フィールドを `val origin: Origin` 1つに集約する。`create`（内国産）は `Origin.Domestic` を、`createImported`（輸入）は `Origin.Imported` を構築する。`registerFoal` は `BloodHorse.create` 委譲なので影響を受けない。
- 父・母は別集約であり、`Origin.Domestic` は集約直接参照ではなく `BloodHorseId` 経由の ID 参照で保持する（jMolecules の集約参照ルールに抵触しない）。`Origin` は `@ValueObject` で Entity / AggregateRoot を参照しない。

### HTTP 契約（レスポンス）

- リソース全体は **単一表現 `BloodHorseResponse` を維持**し（ADR-0008）、共通項は平置きのまま、相互排他な出自だけを入れ子オブジェクト `origin` の `oneOf` ＋ discriminator にする。

  ```jsonc
  // 内国産馬
  { "id": "…", "registration_number": "…", /* …共通… */
    "origin": { "type": "DOMESTIC", "sire_id": "…", "dam_id": "…" } }

  // 輸入馬
  { "id": "…", "registration_number": "…", /* …共通… */
    "origin": { "type": "IMPORTED", "country": "アイルランド", "landing_date": "2024-09-01" } }
  ```

- wire 専用に sealed `OriginDto`（`Domestic` / `Imported`）を `controller` 層に置き、Jackson の `@JsonTypeInfo(use = NAME, property = "type")` ＋ `@JsonSubTypes` で判別子 `type` を出力、springdoc の `@Schema(oneOf = […], discriminatorProperty = "type")` で polymorphic スキーマを生成する。ドメイン `Origin` を wire に直接晒さず `toApi()` で往復する（[ADR-0007](0007-wire-enum-dto-decoupling.md) の decouple 方針と整合）。
- `origin` 配下のフィールド名は入れ子のため接頭辞を省く（`origin_country` → `country`）。

### エンドポイント形（request 側）

- request 側は **2 エンドポイント（`POST /api/bloodHorses` と `:registerImported`）を維持**し、本 ADR では discriminated 化しない。response の discriminated `origin` はエンドポイント形と独立に有効。単一 polymorphic Create に畳む案（[AIP-133](https://google.aip.dev/133)）は入力必須項目が内国産／輸入で大きく異なり request DTO の `oneOf` 設計が重くなるため見送る（[#362](https://github.com/ptiringo/toy-box/issues/362) のコメントで整理。再検討は別途）。

## Consequences（結果・影響）

- 出自の相互排他がコンパイル時に強制され、無効状態（全 null・混在）が型として表現不能になる。`when (origin)` の網羅性で内国産／輸入の取り違えも防げる。
- **ADR-0008 の補足**: 「リソース操作の成功レスポンスは一律で単一リソース表現を返す」を維持しつつ、**相互排他な部分集合だけを discriminated な入れ子オブジェクトにすることを許す**。リソース全体を `oneOf` に割ること（案B）は引き続き採らない。本 ADR は ADR-0008 を Supersede せず、その範囲内での適用指針を足すもの。
- **HTTP 契約の変更を伴う**: レスポンスの `sire_id` / `dam_id` / `origin_country` / `landing_date` が `origin.{sire_id,dam_id}` / `origin.{country,landing_date}` に移り、判別子 `type` が増える。`origin_country` は `origin.country` に名前も変わる。問題詳細（problem+json）の `sire_id` 拡張キー（`SireNotFound` 等）はリソース表現ではないため不変。
- ドメインの sealed `Origin` と wire の `OriginDto` が表裏一体になり、片方の変更がもう片方のマッパー `when` の網羅性で検知できる。
- 規約上の結論（単一リソース表現・wire enum/型の decouple・業務エラーは Result→Controller で ProblemDetail）は既存の ADR-0007 / ADR-0008 / `.claude/rules/api-design.md` に従う。本 ADR はそれらを出自の相互排他に適用した時点の記録。
