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

## エラーレスポンス

[RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) に準拠する。

- `Content-Type: application/problem+json` で返す
- Spring の `org.springframework.http.ProblemDetail` を使う
- 標準フィールド: `type` (URI) / `title` / `status` / `detail` / `instance`
- 業務エラー固有の追加情報は拡張プロパティで保持する（例: `errorCode` / `existingId` 等）
- `type` は当面 `urn:problem-type:{kebab-case-code}` 形式を用い、HTTP で公開する Resolvable URI ができたら差し替える

> AIP-193 (Errors) はレスポンスボディの形（`google.rpc.Status`）を規定するが、本プロジェクトでは REST 寄りの RFC 9457 を採用する。リソース設計など構造系のみ AIP を適用する。
