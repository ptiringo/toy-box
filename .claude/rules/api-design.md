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
- リソース名 / コレクション名は複数形・**camelCase**（[AIP-122](https://google.aip.dev/122)）。例: `/api/bloodHorses`、`/api/breedingResults`
- 標準メソッド `List / Get / Create / Update / Delete`（[AIP-131](https://google.aip.dev/131) ～ [AIP-135](https://google.aip.dev/135)）
- Create は `POST /{collection}` でコレクションに対して行い、作成された resource 全体を `201 Created` で返す（[AIP-133](https://google.aip.dev/133)）
- カスタムメソッドは `POST /{resource}:{verb}`（[AIP-136](https://google.aip.dev/136)）で表す。特定リソースを操作するカスタムメソッド（例: 馬名登録 `:registerName`）は、操作後の resource 全体を返す
- **リソース操作の成功レスポンスは一律でリソース表現を返す**。操作ごとに別レスポンス DTO を作らず、リソース名ベースの単一 DTO（`〜Response`、例 `BloodHorseResponse`）を全操作（Create / カスタムメソッド等）で共用する。操作前は未設定になりうる属性（命名前の `name` 等）は nullable で表す。経緯は [ADR-0008](../../docs/adr/0008-uniform-resource-representation-response.md)
- 単一リソース表現を維持したまま、**相互排他な属性集合だけ**は discriminated な入れ子オブジェクト（`oneOf` ＋判別子 `type`）にしてよい（例: 軽種馬の出自 `origin` ＝ 内国産 `DOMESTIC` / 輸入 `IMPORTED`）。リソース全体を `oneOf` に割ることは依然採らず、共通項は平置きを保つ。Jackson は `@JsonTypeInfo(use = NAME, property = "type")` ＋ `@JsonSubTypes`、springdoc は `@Schema(oneOf = […], discriminatorProperty = "type")`。経緯は [ADR-0020](../../docs/adr/0020-sealed-origin-and-discriminated-origin-subobject.md)

## 命名規約（casing）

URL とボディで casing を使い分ける。AIP の「URL はハードルール、ボディの JSON casing は自由」という非対称をそのまま反映したもの。経緯・却下案は [ADR-0012](../../docs/adr/0012-rest-naming-convention.md)。

| 対象 | casing | 根拠 |
|---|---|---|
| URL コレクション識別子 | **camelCase** | [AIP-122](https://google.aip.dev/122) の must（`/api/bloodHorses`、`/api/breedingResults`）。アンダースコア・ハイフン不可 |
| JSON ボディフィールド | **snake_case** | AIP は JSON casing を縛らない（[AIP-140](https://google.aip.dev/140) が must で縛るのは proto 定義 = snake_case）。意図的に snake_case を採用 |
| カスタムメソッド動詞 | lowerCamelCase | [AIP-136](https://google.aip.dev/136)（`:registerName` 等） |

- ボディの snake_case は `application.yml` の `spring.jackson.property-naming-strategy: SNAKE_CASE` で request/response bean に一括適用する。
- **`ProblemDetail.properties` は `Map<String, Object>` で naming strategy が効かない**。problem+json の拡張キー（`error_code` / `sire_id` 等）は各 `toProblemDetail()` 群の `setProperty(...)` リテラルで直接 snake_case を書く。RFC 9457 の標準フィールド（`type` / `title` / `status` / `detail` / `instance`）は不変。
- enum 値文字列（`MALE` 等）は naming strategy 非対象でそのまま。

## リクエスト / レスポンス DTO とドメインの分離

HTTP 契約（request / response DTO）はドメインモデルから独立させる。とりわけ **enum はドメイン enum を wire に直接晒さず、adapter 層に契約専用の `〜Dto` enum を置いてマッピングする**。

- ドメイン enum を DTO のフィールド型に使うと、ドメイン側の列挙子リネームが HTTP 契約（および生成クライアント）を無言で破壊する。これを断つため `controller` 層に `〜Dto` enum を定義し、`toDomain()` / `toApi()` の網羅 `when` で相互変換する（ドメイン enum の列挙子増減を compile エラーで検知できる）
- 生 String 受けは採らない。`〜Dto` enum なら Jackson の自動デシリアライズ検証（未知値は 400）と OpenAPI の enum スキーマ（許容値の公開）を維持したまま decouple できる
- 変換の置き場所は controller 境界に限る。application 層のコマンド（`〜Command`）はドメイン enum を保持してよい（decouple 対象は wire 契約のみ）
- 列挙子名が現状ドメインと一致していても、それは「今は一致している」だけ。独立性はマッピング層で担保する
- 参考実装: `controller/horse/BloodHorseWireEnums.kt`（`SexDto` / `CoatColorDto` / `BreedTypeDto` / `DnaParentageResultDto`）。この方針を採った経緯・却下案は [ADR-0007](../../docs/adr/0007-wire-enum-dto-decoupling.md) を参照

VO で表す項目（番号・名前など）は素の文字列で DTO に受け、ユースケース内で各 VO の `create()` を通して検証する（enum とは非対称だが、検証ロジックを持つ VO はドメイン層に検証責務を残すため）。

## エラーレスポンス

[RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) に準拠する。

- `Content-Type: application/problem+json` で返す
- Spring の `org.springframework.http.ProblemDetail` を使う
- 標準フィールド: `type` (URI) / `title` / `status` / `detail` / `instance`
- 業務エラー固有の追加情報は拡張プロパティで保持する（例: `error_code` / `existing_id` 等。拡張キーは snake_case。前述「命名規約」を参照）
- `type` は当面 `urn:problem-type:{kebab-case-code}` 形式を用い、HTTP で公開する Resolvable URI ができたら差し替える

> AIP-193 (Errors) はレスポンスボディの形（`google.rpc.Status`）を規定するが、本プロジェクトでは REST 寄りの RFC 9457 を採用する。リソース設計など構造系のみ AIP を適用する。
