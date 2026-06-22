# 0018. 「種付せず」の記録入口を covering 有無で判別する単一 Create にする

- Status: Accepted
- Date: 2026-06-22
- Deciders: ptiringo

## Context（背景・課題）

[ADR-0016](0016-not-covered-as-foaling-outcome-variant.md)（#322 / PR #374）で「種付せず」（その年に種付しなかった年次成績）を**ドメインモデルとして表現可能**にした（`FoalingOutcome.NotCovered` 追加・`BreedingResult.covering` を nullable 化・自己検証ファクトリ `BreedingResult.createUncovered`）。ただし ADR-0016 のスコープはドメインモデルの表現可能性に絞っており、種付せずを**記録する application / controller の入口は未整備**だった（#376）。API 経由では種付せずの `BreedingResult` を作れず、テスト Fixture でしか構築できない状態だった。

既存の繁殖成績の Create 入口は `POST /api/breedingResults`（`RecordCoveringRequest`：繁殖牝馬・種牡馬の繁殖登録ID＋種付日＋証明書番号のフラットなボディ）で「種付した年」だけを起こせる。種付せずは種付（配合相手・種付日・証明書）を伴わず、繁殖年を種付日から導出できないため繁殖年を明示的に受け取る必要があり、フィールド構成が大きく異なる。この入口をどう設けるかで設計が分岐した。

検討した代替案:

- **案B: 別カスタムメソッド**。既存の covered パス（`POST /api/breedingResults`）は無変更のまま、種付せず専用の `POST /api/breedingResults:recordUncovered` 等を足す。blast radius は最小で実装も独立するが、**同一コレクションに2系統の Create が並ぶ**。種付せずの記録は新しい年次成績リソースを**作成する**操作であり標準メソッド Create に該当する（[AIP-133](https://google.aip.dev/133)）。これをカスタムメソッド（[AIP-136](https://google.aip.dev/136)＝標準メソッドで表せない操作）に倒すのは AIP の意図と食い違う。年次成績という単一の報告単位が、生成経路だけ2つの入口に割れることにもなる。

## Decision（決定）

**`POST /api/breedingResults` を、リクエストの `covering` の有無で「種付した年」と「種付しなかった年（種付せず）」を判別する単一の Create に統合する。**

- リクエスト DTO を `RecordCoveringRequest`（フラット）から `RecordBreedingResultRequest` へ置換する。種付の項目（種牡馬の繁殖登録ID・種付日・証明書番号）を `covering: CoveringRequest?` にネストし、`covering` が null かどうかで分岐する:
  - `covering != null`: その年の種付を記録する（`RecordCoveringUseCase`）。繁殖年は種付日から導出されるため、トップレベルの `breeding_year` は不要（指定されても無視する）。
  - `covering == null`: 種付せずを記録する（新設 `RecordUncoveredUseCase`）。繁殖年は種付日から導出できないため `breeding_year` が必須。
- application 層に `RecordUncoveredUseCase` を新設する。`RecordUncoveredCommand(breedingRegistrationId, breedingYear)` を受け、繁殖登録を解決して `BreedingResult.createUncovered` を呼び、終端の `BreedingResult` を保存する。失敗バリアントは `BreedingRegistrationNotFound` と `PreconditionViolated(NotBroodmareForUncovered)`（種付記録と異なり配合相手の検証はないため前提条件違反は1種類）。
- Controller は `record` ハンドラ内で `covering` の有無を見て2つのユースケースへ振り分ける。種付せずで `breeding_year` が欠ける入力不正は、Controller 境界で 400 `missing-breeding-year` を返す（`:reportFoaling` の `toOutcome()` と同じ「リクエスト変換が `Result<_, ProblemDetail>` を返し `orThrowProblem()` で funnel に委譲する」パターン）。
- ドメインの前提条件違反は種付記録と対称に 422 とする（`breeding-registration-not-found` / `not-broodmare`）。
- 成功レスポンスは covered / uncovered とも一律で単一リソース表現 `BreedingResultResponse` を返す（[ADR-0008](0008-uniform-resource-representation-response.md)）。種付せずでは種付関連項目（`stallion_id` / `covering_date` / `certificate_number`）が null、`outcome` は `NOT_COVERED`。

この判別はドメインの `covering: Covering?` が nullable であること（ADR-0016）と構造的に対称であり、年次成績という単一の報告単位を単一のリソース・単一の Create 入口で扱える。

## Consequences（結果・影響）

- 年次成績の生成経路が単一の Create 入口に集約され、AIP-133（Create はコレクションへの `POST`）に沿う。ドメインの `covering` nullable とも対称になり、wire 契約とモデルの形が一致する。
- **HTTP 契約の変更を伴う**: `POST /api/breedingResults` のボディが、種付項目をトップレベルに平らに置く形から `covering` ネスト＋トップレベル `breeding_year` へ変わる。covered のリクエストも `covering` でくるむ必要がある。
- `breeding_year` が「種付せずのときだけ必須」という条件付き必須フィールドになる。covered では `covering_date` から導出するため `breeding_year` は無視され、トップレベルに2つの年の出所がありうる非対称を Controller の分岐で吸収する。この条件付き必須は `:reportFoaling` の `foaling_date`（生産のときだけ必須）に前例があり、検証は Controller 境界の DTO 変換（`Result<_, ProblemDetail>`）に置く。
- 種付せず専用の独立したエンドポイントを持たないため、将来 covered / uncovered で表現が大きく乖離した場合は単一 Create の分岐が膨らみうる。その兆候が出たら再分割を検討する。
- 規約上の結論（リソース操作は単一表現 `〜Response` を返す・wire enum は DTO で decouple・業務エラーは Result→Controller で ProblemDetail）は既存の [ADR-0007](0007-wire-enum-dto-decoupling.md) / [ADR-0008](0008-uniform-resource-representation-response.md) / `.claude/rules/api-design.md` に従う。本 ADR はそれらを種付せずの記録入口に適用した時点の記録。
