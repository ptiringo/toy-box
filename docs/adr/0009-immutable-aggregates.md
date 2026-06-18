# 0009. ドメイン集約はイミュータブルに保ち、状態遷移は新インスタンスで表す

- Status: Accepted
- Date: 2026-06-18
- Deciders: Matsui

## Context（背景・課題）

馬名登録（issue #264）の実装で、軽種馬集約 `BloodHorse` に「命名状態」を持たせる必要が出た。出生時は未命名で、後日の馬名登録で一度だけ命名される、というライフサイクルをモデル化する。

最初の実装は、集約に `var name: HorseName?`（`private set`）を持たせ、`assignName` が自身の `name` を書き換える **mutable な方式**だった。しかしこの方式には次の弱点がある。

- 集約への共有参照を持つ別の箇所から、状態がいつの間にか変わりうる（予期しない副作用）。
- Repository が返した同一インスタンスを複数箇所が握っていると、片方の操作が他方に漏れる。
- 「元の状態」と「遷移後の状態」を同時に扱えない（テストや比較で不便）。

ドメインモデルはできるだけイミュータブルに保ちたい、という方針を踏まえ、mutable をやめて**状態遷移を新インスタンスで表す**方式に切り替えた。

実装上の論点として、Kotlin の `data class` が提供する `copy()` は魅力的だが、本プロジェクトのエンティティは基底 `Entity<ID>` が `equals` / `hashCode` を **ID ベースで `final` 実装**している（[ADR 化されていないが CLAUDE.md「Entity パターン」の方針]）。`data class` は `equals` / `hashCode` を自動生成し、この `final` 実装と衝突するため使えない。したがって写像は **private constructor + 手書きの `copy` メソッド**で行う。

## Decision（決定）

- ドメイン集約（`@AggregateRoot` / `@Entity`）は**イミュータブルに保つ**。状態を表すプロパティは `val` とし、`var` を持たせない。
- 状態遷移は対象インスタンスを書き換えず、**同一性（`id`）を引き継いだ新しいインスタンスを返すメソッド**で表す。失敗しうる遷移は `Result<新インスタンス, エラー>` を返す（例: `BloodHorse.assignName(horseName): Result<BloodHorse, HorseAlreadyNamed>`）。
- 論理的な同一性は基底 `Entity<ID>` の **ID ベース等価性**で担保する。属性が違っても `id` が同じなら同一エンティティとみなされるため、写像後の新インスタンスは元と等価。
- エンティティに **`data class` は使わない**（ID ベースの `final equals` / `hashCode` と衝突するため）。写像は `private constructor` ＋ 手書きの `copy` メソッドで行い、生成口（`of` 等）の封じ込め方針（[ADR-0005] と CLAUDE.md「Entity パターン」）も維持する。

守るべきルールの結論は [CLAUDE.md](../../CLAUDE.md)「Entity パターン」に置く。参考実装は `domain/horseracing/model/horse/bloodhorse/BloodHorse.kt`（`assignName` が命名済みの新 `BloodHorse` を返す）。経緯は issue #264。

## Consequences（結果・影響）

- 集約への共有参照経由で状態が予期せず変わることがなくなった。`assignName` は元のインスタンスを変更しないため、「命名前」と「命名後」を同時に保持・比較できる（テストでも検証しやすい）。
- 状態遷移のたびに新インスタンスを生成するコストが生じるが、集約は粗粒度で頻繁に作り直すものではなく、実害は小さい。
- 手書きの `copy` は、将来プロパティを追加したときに**引き継ぎ漏れがコンパイルエラーにならない**（黙って旧値・null が入る）リスクがある。プロパティ追加時は `copy` の更新を忘れないこと。この脆さは将来 ArchUnit 等で機械強制する余地がある（集約に `var` を禁止する／`data class` を禁止する規約化を検討）。
- 本決定は全集約に適用する。新しい集約・エンティティも `val` のみで構成し、状態遷移は新インスタンス返却で表す。
