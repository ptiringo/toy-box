---
paths:
  - "src/main/kotlin/com/example/api/controller/**/*.kt"
  - "src/test/kotlin/com/example/api/controller/**/*.kt"
---

# API 設計規約

REST API は以下のスタイルガイドに準拠する。

## リソース設計

[Google AIP](https://google.aip.dev/) 準拠とする。

- リソース指向設計（[AIP-121](https://google.aip.dev/121)）
- リソース名 / コレクション名は複数形・小文字スネークケース（[AIP-122](https://google.aip.dev/122)）
- 標準メソッド `List / Get / Create / Update / Delete`（[AIP-131](https://google.aip.dev/131) ～ [AIP-135](https://google.aip.dev/135)）
- Create は `POST /{collection}` でコレクションに対して行い、作成された resource 全体を `201 Created` で返す（[AIP-133](https://google.aip.dev/133)）

## リクエスト / レスポンス DTO とドメインの分離

HTTP 契約（request / response DTO）はドメインモデルから独立させる。とりわけ **enum はドメイン enum を wire に直接晒さず、adapter 層に契約専用の `〜Dto` enum を置いてマッピングする**。

- ドメイン enum を DTO のフィールド型に使うと、ドメイン側の列挙子リネームが HTTP 契約（および生成クライアント）を無言で破壊する。これを断つため `controller` 層に `〜Dto` enum を定義し、`toDomain()` / `toApi()` の網羅 `when` で相互変換する（ドメイン enum の列挙子増減を compile エラーで検知できる）
- 生 String 受けは採らない。`〜Dto` enum なら Jackson の自動デシリアライズ検証（未知値は 400）と OpenAPI の enum スキーマ（許容値の公開）を維持したまま decouple できる
- 変換の置き場所は controller 境界に限る。application 層のコマンド（`〜Command`）はドメイン enum を保持してよい（decouple 対象は wire 契約のみ）
- 列挙子名が現状ドメインと一致していても、それは「今は一致している」だけ。独立性はマッピング層で担保する
- 参考実装: `controller/horse/BloodHorseWireEnums.kt`（`SexDto` / `CoatColorDto` / `BreedTypeDto` / `DnaParentageResultDto`）

VO で表す項目（番号・名前など）は素の文字列で DTO に受け、ユースケース内で各 VO の `create()` を通して検証する（enum とは非対称だが、検証ロジックを持つ VO はドメイン層に検証責務を残すため）。

## エラーレスポンス

[RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) に準拠する。

- `Content-Type: application/problem+json` で返す
- Spring の `org.springframework.http.ProblemDetail` を使う
- 標準フィールド: `type` (URI) / `title` / `status` / `detail` / `instance`
- 業務エラー固有の追加情報は拡張プロパティで保持する（例: `errorCode` / `existingId` 等）
- `type` は当面 `urn:problem-type:{kebab-case-code}` 形式を用い、HTTP で公開する Resolvable URI ができたら差し替える

> AIP-193 (Errors) はレスポンスボディの形（`google.rpc.Status`）を規定するが、本プロジェクトでは REST 寄りの RFC 9457 を採用する。リソース設計など構造系のみ AIP を適用する。
