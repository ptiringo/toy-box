# 0026. API リクエストバリデーションは VO 中心を維持し Bean Validation を当面採らない

- Status: Accepted
- Date: 2026-06-24
- Deciders: Matsui

## Context（背景・課題）

API リクエストのバリデーションを、宣言的 Bean Validation（jakarta.validation / `@Valid` / hibernate-validator）で
controller 境界に導入するか否かを決める必要があった（#409）。現状は Bean Validation の依存も制約注釈も持たず、
検証を二層で実現している。

1. **構造検証**（必須・型・JSON 形状・未知 enum 値）＝ Jackson ＋ Kotlin 非 null 型。欠落／型不一致／未知 enum は
   読み取り段階で弾かれ、`HttpMessageNotReadableException` → `GlobalExceptionHandler`（`ResponseEntityExceptionHandler`）が
   RFC 9457 形式の **400** に描画する。
2. **形式・業務検証**（ブランク・区間の逆転など）＝ ユースケース内で各 Value Object の `create()`（`Result`）を通し、
   固有 `error_code` 付きの **400** に描画する。

この構えは既存方針に沿う（[api-design.md](../../.claude/rules/api-design.md)「VO で表す項目は素の文字列で受け、
ユースケース内で各 VO の `create()` を通して検証する」／[ADR-0014](0014-self-validating-factory-over-confinement.md) 自己検証ファクトリ／
[ADR-0007](0007-wire-enum-dto-decoupling.md) wire enum の DTO 分離／[error-handling.md](../../.claude/rules/error-handling.md) Result-first）。

### 一般論（流派で標準が割れる）

- **汎用 Spring Boot の標準は Bean Validation**。DTO に `@Valid` ＋ `@NotBlank/@Size/@Pattern` を宣言するのが多数派で、
  fail-fast・全フィールドエラー一括（`MethodArgumentNotValidException`）・springdoc による OpenAPI への制約自動公開・低記述量が利点。
- **DDD / VO 流派の標準はドメインでの検証**。形式・不変条件は値オブジェクトの生成口を単一の真実とし、境界（DTO）は薄く保つ
  （"parse, don't validate"）。DTO の制約注釈は**ドメイン不変条件の二重化＝真実が 2 つに割れて drift する**として、あえて使わない。

本プロジェクトは ADR-0007 / ADR-0014 / api-design.md で明確に後者（VO 流派）にコミット済みである。

### 検討した代替案（#409 のたたき台 A〜E）

- **A. 現状維持（VO 中心・Bean Validation なし）**: 一貫・低コスト。OpenAPI への制約公開とフィールド単位の即時 400 は諦める。
- **B. 構造のみ境界・形式は VO**: Kotlin 非 null が既に presence を担うため上積みが薄い。
- **C. 形式を Bean Validation に寄せ VO 検証を削る**: 単一ソース・公開○だが、HTTP 以外の生成経路（テスト・他ユースケース）で
  不正な VO を作れてしまい DDD のガードを失う。却下。
- **D. Bean Validation ＋ VO の二重（belt-and-suspenders）**: 境界 fail-fast ＋ OpenAPI 公開 ＋ ドメイン保証。
  代償は二重化と drift（単一ソース化＋一致テストで緩和が要る）。
- **E. VO 中心のまま OpenAPI 公開だけ別途**（DTO に springdoc `@Schema(pattern=…, minLength=…)` を手書き）:
  実行時の二重検証は避けつつ公開。ただし `@Schema` も別ソースで drift しうる・強制はしない。

Bean Validation 固有の実利は「OpenAPI への制約公開」と「複数エラーの一括報告」の 2 つだが、現状の VO 不変条件はほぼ
「非ブランク」のみで桁数・形式が未確定（証明書番号の桁数・区域体系など）であり、**今は公開すべき制約が薄く**、
複数エラー一括を要する form 的 API の要件も無い。よって両実利とも**現時点では効きが弱い**。

## Decision（決定）

**当面は A（VO 中心・Bean Validation なし）を維持する。** すなわち:

- リクエストの**構造検証は Jackson ＋ Kotlin 非 null 型**、**形式・業務検証は各 VO の `create()`（`Result`）**に置く現状の二層を続ける。
- jakarta.validation / hibernate-validator の依存も `@Valid` 等の制約注釈も**導入しない**。
- セマンティック検証（参照先リソースの存在・ロール・一意性など）は引き続きドメイン／ユースケースに置く（本決定の対象外で不変）。

**再評価トリガを明示する。** フィールドの**形式（桁数・パターン・区域体系など）が具体化**し（例: [#312](https://github.com/ptiringo/toy-box/issues/312) 個体識別、登録番号体系の確定）、
**OpenAPI への制約公開が主目的化**した時点で、**E（springdoc `@Schema` による公開のみ／実行時の二重検証はしない）を第一候補**として再評価する。
複数エラーの一括報告を重視する要件が出た場合は **D** を別途検討する。再評価の結果方針を変える場合は、本 ADR を上書きせず
新しい ADR を起こして本 ADR を Superseded にする。

## Consequences（結果・影響）

- **得るもの**: 検証の真実が VO に一本化され、生成経路が増えてもバイパス不能（DDD ガード）。wire 契約とドメイン不変条件の
  二重化・drift（ADR-0007 が避けてきた結合）を持ち込まない。依存とコードが増えない。
- **引き受けるもの**: OpenAPI がフィールド制約を公開しないため、クライアント／生成 SDK は 400 を食らうまで形式規則を知れない
  （現状は公開すべき制約が薄いので影響は限定的）。エラーは最初の失敗で短絡し 1 件ずつ返す（複数エラー一括報告はしない）。
- **運用上の注意**: 新しいリクエスト項目も同じ二層（欠落＝Jackson 400／形式＝VO 検証 400）に乗せる。直近 #404 の
  `covering_place` / `stud_certificate`、#277 の品種も同様。形式が固まったら上記トリガで E/D を再評価する。
- 結論（守るべきルール）は [api-design.md](../../.claude/rules/api-design.md) と [error-handling.md](../../.claude/rules/error-handling.md) に
  既存の記述として置かれており、本 ADR はその「なぜ・却下案・再評価条件」を補う。
