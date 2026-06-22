# 0016. 「種付せず」を FoalingOutcome の区分として表し、covering を nullable 化する

- Status: Accepted
- Date: 2026-06-22
- Deciders: ptiringo

## Context（背景・課題）

繁殖成績報告（様式第14号）は、繁殖牝馬ごとの年次成績として8区分（生産／不受胎／流産／双子流産／死産／双子死産／生後直死／双子生後直死／**種付せず**）を報告する。#265（PR #321）で繁殖成績のドメイン層を実装した際、スコープを絞るため「種付せず」を除外した。

当時の `BreedingResult` 集約は種付（`Covering`）を必須としてモデル化しており、

- `covering: Covering`（非 null）を `create` の必須引数で受け取る
- 種付年は `covering.coveringDate` から導出する（`coveringYear` getter）
- `FoalingOutcome` は「**種付後に生じる**帰結」の sealed 語彙

という前提に立っていた。このため「その年に種付しなかった」年次成績を表現できない。種付せずは種付が存在しない帰結であり、上記前提（covering 必須・年は種付日から導出・分娩結果は種付後の帰結）をすべて崩す。#322 でこれをモデル化するにあたり、設計の分岐を整理する必要があった。

検討した代替案:

- **案B: 種付せずを別概念（別レコード／別集約）として表す**。`BreedingResult` は種付必須のまま不変とし、種付せずを `UncoveredBreedingYear(broodmareRegistrationId, year)` 等の別型で持つ。集約は清潔に保てるが、年次の繁殖成績という**単一の報告単位**が2つの型に割れ、年次集計（#325 の受胎率・生産率・登録率や提出期限制約）でレポート時に2型を union する必要が生じる。様式第14号が8区分を**単一の「繁殖成績」列の値**として扱う現実とも乖離する。

## Decision（決定）

**「種付せず」を `FoalingOutcome` の区分 `NotCovered` として追加し（案A）、`BreedingResult.covering` を nullable 化する。**

- `FoalingOutcome` を「分娩結果」から「**年次の繁殖成績区分**」へと意味を広げ、8区分目 `NotCovered`（`data object`）を加える。`NotCovered` だけは種付を伴わない区分である。
- `BreedingResult` に繁殖年 `breedingYear: Year` を**明示フィールド**として持たせる（種付せずは種付日から年を導出できないため）。種付した年は `breedingYear == covering の年` を不変条件で保証する。`coveringYear` getter は廃し `breedingYear` に統一する。
- `covering: Covering?` を nullable 化し、covering と区分の整合を**集約の不変条件**（private constructor の `require`）で強制する:
  - `covering == null` ⇔ `outcome == NotCovered`（種付せずは生成時に終端）
  - `covering != null` ⇒ `outcome != NotCovered` かつ `breedingYear == 種付日の年`
- 種付せずの生成は専用の自己検証ファクトリ `BreedingResult.createUncovered(broodmareRegistration, breedingYear)` に限る（[ADR-0014](0014-self-validating-factory-over-confinement.md) の方針に沿い、繁殖牝馬ロールを自己検証して `Result` を返す）。失敗は1種類のため単一型 `NotBroodmareForUncovered` とする。
- 種付せずは終端レコードのため `recordFoaling` の対象にならない（outcome が既に確定済みのため二重報告として弾かれ、加えて `NotCovered` を分娩結果として報告する経路は `require` で塞ぐ）。
- HTTP 契約では `FoalingOutcomeDto.NOT_COVERED` を追加し、`BreedingResultResponse` の種付関連項目（`stallionId` / `coveringDate` / `certificateNumber`）と `outcome` を nullable 化する。`:reportFoaling` カスタムメソッドは種付済み成績への分娩結果報告であり、`NOT_COVERED` は受け付けず 400 を返す（種付せずの記録経路は分娩結果報告とは別）。

## Consequences（結果・影響）

- 年次の繁殖成績が**単一の集約・単一の区分語彙**で表現でき、#325 の年次集計が1つの `BreedingResult` 列を走査するだけで済む。様式第14号の8区分という現実にも一致する。
- `FoalingOutcome` の意味が「分娩結果」から「年次成績区分」へ広がった。`NotCovered` は名目上 `FoalingOutcome` に属すが分娩を伴わない唯一の区分であり、この非対称は covering との整合を集約の不変条件で吸収する。
- `covering` の nullable 化により、covering を参照する側（`RegisterFoalUseCase` の父解決、レスポンスの種付項目）は null を扱う必要がある。`RegisterFoalUseCase` は種付せず成績から産駒登録が呼ばれた場合、父解決の前に既存の `RegisterFoalError.NotLiveFoal` 前提条件違反へ寄せて短絡する（種付せずから生まれる産駒は存在しない）。
- レスポンス DTO のフィールド名が `coveringYear`→`breedingYear`（wire は `covering_year`→`breeding_year`）に変わり、種付関連3項目が nullable になる。HTTP 契約の変更を伴う。
- 種付せずを**記録する** application / controller の入口（`createUncovered` ユースケースとエンドポイント）は本決定のスコープ外。本 ADR はドメインモデルの表現可能性を確立するもので、駆動経路は別途整備する（#322 のフォロー）。
- 集約の不変条件・ファクトリ方針は [ADR-0009](0009-immutable-aggregates.md)（イミュータブル集約）・[ADR-0014](0014-self-validating-factory-over-confinement.md)（自己検証ファクトリ）に整合する。
