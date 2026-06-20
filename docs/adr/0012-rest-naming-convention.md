# 0012. REST 命名規約を URL=camelCase / ボディ=snake_case に確定する

- Status: Accepted
- Date: 2026-06-21
- Deciders: ptiringo

## Context（背景・課題）

REST API の命名（URL とボディの casing）が不揃いで、ドキュメントと実装も食い違っていた。

- **URL コレクション識別子**: snake_case（`/api/blood_horses`、`/api/breeding_results`）
- **JSON ボディフィールド**: Jackson デフォルトの camelCase（`registrationNumber` / `coveringYear` など）
- **`.claude/rules/api-design.md` の AIP-122 誤読**: 「コレクション名は小文字スネークケース（AIP-122）」と記載していたが、AIP-122 は camelCase を要求している

### AIP の典拠（原典確認済み）

- **URL（コレクション識別子）— [AIP-122](https://google.aip.dev/122)**: `Collection identifiers **must** be in camelCase`、かつ `/[a-z][a-zA-Z0-9]*/`（アンダースコア・ハイフン不可）、複数形。**現状の snake_case は AIP-122 違反**。kebab-case も非準拠。
- **ボディ（フィールド名）— [AIP-140](https://google.aip.dev/140)**: must で縛るのは **proto 定義 = `lower_snake_case`**。JSON 表現の casing は明示していない。Google API のボディが camelCase に見えるのは proto3 の JSON マッピング既定（非規範・パーサは両方受理）であって AIP の要求ではない。**本プロジェクトは proto ベースではないため JSON casing は自由**で、snake_case にしても AIP 違反にならない。
- **カスタムメソッド動詞 — [AIP-136](https://google.aip.dev/136)**: lowerCamelCase。現状の `:registerName` / `:reportFoaling` は準拠済み。

### 検討した代替案

- **全 camelCase（URL も ボディも camelCase）**: AIP-122 は満たすが、ボディを camelCase にする積極的な根拠が「proto3 既定の見た目に合わせる」しかない。本プロジェクトは proto ベースでないためその制約に従う理由がなく、却下。
- **全 snake_case（URL も ボディも snake_case）**: ボディは妥当だが URL が AIP-122 違反になるため却下。
- **kebab-case（URL）**: AIP-122 の `/[a-z][a-zA-Z0-9]*/` に反する（ハイフン不可）ため却下。

## Decision（決定）

REST 命名規約を以下に確定する。

| 対象 | casing | 根拠 |
|---|---|---|
| URL コレクション識別子 | **camelCase** | AIP-122 の must（`blood_horses` → `bloodHorses`、`breeding_results` → `breedingResults`） |
| JSON ボディフィールド | **snake_case** | AIP は JSON casing を縛らない。snake_case が AIP-140 の「真の」フィールド名であり、意図的に採用する |
| カスタムメソッド動詞 | lowerCamelCase | AIP-136（現状維持） |

URL=camelCase / ボディ=snake_case という非対称は、AIP の「URL はハードルール、ボディの JSON casing は自由」をそのまま反映したもの。

実装方針:

- URL は `controller/**` の `@PostMapping` 等のパスを camelCase へ変更する。
- ボディは `application.yml` の `spring.jackson.property-naming-strategy: SNAKE_CASE` で request/response bean に一括適用する。
- **`ProblemDetail.properties` は `Map<String, Object>` で naming strategy が効かない**。problem+json の拡張キー（`error_code` / `sire_id` 等）は各 `toProblemDetail()` 群の `setProperty(...)` リテラルで直接 snake_case を書く。RFC 9457 の標準フィールド（`type` / `title` / `status` / `detail` / `instance`）は不変。
- enum 値文字列（`MALE` 等）は naming strategy 非対象でそのまま。

## Consequences（結果・影響）

- URL が AIP-122 準拠になり、ドキュメント（`.claude/rules/api-design.md`）の誤記も訂正された。命名規約が URL / ボディともに一意に定まり、新規エンドポイントの判断に迷いがなくなる。
- ボディの snake_case 化は naming strategy で一括適用されるため、新しい DTO はフィールドを camelCase の Kotlin プロパティとして書くだけで自動的に snake_case にシリアライズされる（追加作業不要）。
- ただし `ProblemDetail` の拡張キーだけは naming strategy の枠外であり、`setProperty(...)` のキー文字列を手で snake_case に保つ必要がある（規約の穴。レビュー時の確認点）。将来 Detekt / ArchUnit での機械強制を検討する余地がある。
- 結論（守るべきルール）は `.claude/rules/api-design.md` の「命名規約（casing）」節に置く。本 ADR はその経緯・典拠・却下案の記録。
