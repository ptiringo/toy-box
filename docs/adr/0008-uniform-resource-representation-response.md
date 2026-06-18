# 0008. REST リソース操作の成功レスポンスは一律でリソース表現を返す

- Status: Accepted
- Date: 2026-06-18
- Deciders: Matsui

## Context（背景・課題）

軽種馬リソースに操作が増えたタイミングで、操作ごとに成功レスポンスの形がばらつき始めた。

- 血統登録（Create, `POST /api/blood_horses`）は登録された軽種馬の全項目を返す `RegisterBloodHorseResponse` を返していた。
- 馬名登録（カスタムメソッド `POST /api/blood_horses/{id}:registerName`）は当初、`id` + `name` だけの部分レスポンス `RegisterHorseNameResponse` を返す実装だった。

同じ「軽種馬」リソースに対する操作なのに、操作ごとにレスポンスの形が異なると、クライアントは操作別の形を扱う必要があり、リソース表現が一意でなくなる。また部分レスポンスは「命名後にその馬が今どういう状態か」を全部は返さないため、観測のために別途 Get が要る。

[Google AIP](https://google.aip.dev/) の規定を確認した（`google.aip.dev` をネットワーク許可に追加して原文を参照）。

- **AIP-133 (Create)**: *"The response message **must** be the resource itself. There is no `CreateBookResponse`."* — Create は**リソース表現そのものを返すのが必須**で、`〜Response` ラッパーを作ってはならない。
- **AIP-136 (Custom methods)**: *"Custom methods **should** return a response message matching the RPC name, with a `Response` suffix."* ただし *"When operating on a specific resource, a custom method **may** return the resource itself."* — 専用 Response がデフォルトだが、**特定リソースを操作するカスタムメソッドはそのリソースを返してよい**。

馬名登録は「特定の軽種馬の状態遷移（命名）」であり、AIP-136 の「特定リソースを操作する場合はリソース返却可」に該当する。Create が AIP-133 でリソース返却必須である以上、両者を揃えてリソース表現を一律で返すのが一貫し、規約にも忠実になる。

## Decision（決定）

- リソースに対する操作（標準メソッド Create / Update / Get、および**特定リソースを対象とするカスタムメソッド**）の成功レスポンスは、**一律でそのリソースの表現全体を返す**。
- リソース表現は**単一の DTO**（リソース名ベースの `〜Response`、例 `BloodHorseResponse`）に統合し、全操作で共用する。**操作ごとの部分レスポンス DTO（`Register〜Response` 等）は作らない**。
- 操作前は未設定になりうる属性（命名前の `name` など）は **nullable** で表す。
- 変換は `BloodHorse.toResponse()` のような単一のマッパーに集約する。

参考実装は `controller/horse/BloodHorseResponse.kt`（`BloodHorseResponse` + `toResponse()`）と `controller/horse/BloodHorseController.kt`（`register` / `registerName` がともに `BloodHorseResponse` を返す）。守るべきルールの結論は [.claude/rules/api-design.md](../../.claude/rules/api-design.md)「リソース設計」に置く。経緯は issue #264。

## Consequences（結果・影響）

- create も馬名登録も同じリソース表現を返すため、クライアントは操作によらず一意の形を扱える。OpenAPI スキーマもリソースあたり 1 つに収まる。
- カスタムメソッドの成功レスポンスから、操作で更新されない項目も含めてリソースの現在状態が観測できる（命名結果を見るための別 Get が不要）。
- リソース表現に「操作前は未設定」の属性が nullable で混ざる（例: create 直後のレスポンスは `name: null`）。これはリソースのライフサイクルを素直に反映したものとして許容する。
- 将来、あるカスタムメソッドが「リソース以外の付加情報」を返す必要が出た場合は、AIP-136 の `{Verb}{Resource}Response` を作る判断余地を残す。その場合は本 ADR を追補・更新する。
- 本決定は BloodHorse 限定ではなく、今後の全リソース（jockey 等）に適用する。`〜Response` を操作別に量産していた箇所は、リソース表現の単一 DTO へ寄せる。
