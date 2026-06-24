# 0028. controller アダプターの DTO を役割別サブパッケージ（request/ + problem/）へ整理する

- Status: Accepted
- Date: 2026-06-24
- Deciders: Matsui

## Context（背景・課題）

[PR #305](https://github.com/ptiringo/toy-box/pull/305)（馬名登録）で `controller/horse/` が肥大化した。1 リソースに複数操作（血統登録 Create・輸入馬登録・馬名登録カスタムメソッド）が付いた結果、Controller・リクエスト DTO・リソース表現（`〜Response`）・wire enum・Problem 変換が同一パッケージに平置きで混在し、操作が増えるたびに Request と Problem がさらに増える見通しになった（#306）。

整理の方針を確定し、`horse` / `jockey` / `breeding` の全リソースに横展開する。あわせて、放置すると再び平置きへ戻る力が働くため、機械強制できる部分は ArchUnit で固定する。

### 制約・前提

- **単一リソース表現**（[ADR-0008](0008-uniform-resource-representation-response.md)）: リソースの全操作が共通の `〜Response` を返す。Response はリソースの中心物。
- **wire enum の decouple**（[ADR-0007](0007-wire-enum-dto-decoupling.md)）: `〜Dto` enum と `toDomain()`/`toApi()` マッピング。wire enum は request（入力）と response（出力）の双方が使う共有物。
- **エラー描画の funnel**（[error-handling.md](../../.claude/rules/error-handling.md)）: 業務エラーは Controller 境界で `toProblemDetail()` 拡張関数により `ProblemDetail` へ写し、`orThrowProblem()` で中央 funnel に委ねる。Problem マッパーは操作（失敗バリアント）ごとに増える。
- ArchUnit（`com.example.api.controller..`）/ Kover（`packages("com.example.api.controller")`）はいずれもサブパッケージを包含する。

### 検討した代替案

| 案 | 内容 | 評価 |
|---|---|---|
| **A: `request/` + `problem/` 隔離（採用）** | Controller と単一リソース表現クラスタ（`〜Response`＋共有 wire enum＋入れ子 DTO）を resource 直下に残し、増える Request と Problem だけサブパッケージへ | 中心物（Response）を一等地に保ったまま、肥大化の主因だけを隔離できる |
| B: `dto/` + `problem/` の 2 分割 | request / response / wire enum をまとめて `dto/` に、Problem を `problem/` に | Response の中心性が埋もれる。request と response を同列に扱う必然が薄い |
| C: 純粋三分割 `request/` `response/` `problem/` | 役割で完全に三分 | wire enum が request / response の双方に従属するため置き場所が決まらず破綻 |
| D: 平置き維持・命名規約のみ | 移動せず `〜Request`/`〜Response`/`〜Problem` の命名で表す | churn ゼロだが肥大化の見通しは現状のまま。機械強制も効かせにくい |

## Decision（決定）

各リソース `controller/<resource>/`（`horse` / `jockey` / `breeding`）を次のとおり配置する（案 A）。

- **resource 直下（root）**: `@RestController`、単一リソース表現 `〜Response`、その表現が露出する共有 wire enum（`〜WireEnums`・`〜Dto`）・入れ子 DTO。
- **`request/`**: リクエストボディ DTO（`〜Request`）と `toCommand()` 等の入力マッピング。
- **`problem/`**: 業務エラー → `ProblemDetail` 変換（`〜Problem.kt` の `toProblemDetail()` 拡張関数群）。**リソース単位で 1 ファイルに集約**する（例: `BloodHorseProblem.kt` が馬名登録・血統登録・輸入馬登録の全マッパーを持つ）。

機械強制の切り分け:

- **強制する（ArchUnit）**: 「`〜Request` は `request/` に」「`〜Problem.kt`（facade `〜ProblemKt`）は `problem/` に」。`ArchitectureTest` の `requestDtosResideInRequestSubpackage` / `problemMappersResideInProblemSubpackage` で固定し、ルールが空振りしない（実際に違反を検出する）ことを `ControllerPackageLayoutRuleTest` のミューテーションで担保する。
- **強制しない（レビュー担保）**: 「Controller＋Response クラスタが root にある」という意味的グルーピング。ArchUnit は命名・パッケージ・型は検査できるが「リソースの中心表現かどうか」は表せない（[ADR-0008](0008-uniform-resource-representation-response.md) のグルーピング規約を機械強制しないのと同種の判断）。Request / Problem を所定のサブパッケージへ強制すれば、残りは自然に root に残る。

## Consequences（結果・トレードオフ）

- 肥大化の主因（操作ごとに増える Request / Problem）が resource 直下から消え、見通しが改善する。新リソースは `<resource>/`＋`request/`（必要なら `problem/`）を切るだけで同じ構成になる。
- Problem マッパーが Response ファイルに同居していた `horse` / `breeding` は、マッパーを `problem/<Resource>Problem.kt` へ抽出した。これにより Response ファイルはリソース表現＋`toResponse()` のみとなり、責務が一本化した。
- サブパッケージ化は ArchUnit / Kover の対象を変えない（両者ともサブパッケージを包含）。controller の成熟カバレッジゲートはそのまま効く。
- ルールは命名規約（`〜Request` / `〜Problem.kt`）に結合する。Problem マッパーをクラス（`object`/`class`）で書く経路を将来許す場合は、ルールの述語（現状 facade `〜ProblemKt` を対象）を見直す。
