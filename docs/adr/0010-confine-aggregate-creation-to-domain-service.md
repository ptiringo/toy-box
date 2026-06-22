# 0010. 集約をまたぐ前提条件を持つ生成口はドメインサービスに封じ込める（of は internal、テストは Object Mother）

- Status: Superseded by [ADR-0014](0014-self-validating-factory-over-confinement.md)
- Date: 2026-06-20
- Deciders: Matsui

> **注記（2026-06-22）**: 本 ADR の「生成口をドメインサービスに封じ込める（`internal` 化）」という決定は [ADR-0014](0014-self-validating-factory-over-confinement.md) で覆された。前提条件は父・母を引数で受け取れば集約自身の `public` 生成ファクトリで自己検証でき（`Jockey.create` と同じ）、検証専用サービスも封じ込めも不要になるため。以下は当初（封じ込め方式）の記録としてそのまま残す。

## Context（背景・課題）

血統登録（issue #266 / PR #263）の実装で、軽種馬集約 `BloodHorse` の生成のしかたが問題になった。

血統登録は「血統及び個体識別を明らかにする登録」であり、`BloodHorse` を生成してよいのは次の前提条件を検証し終えた後に限られる。

- 父が雄であること
- 母が雌であること
- 申告された父母との DNA 型による親子判定に矛盾がないこと
- 仔の品種が父母の品種と整合すること

これらはいずれも**父・母という別集約（別の `BloodHorse`）を参照して初めて検証できる**。つまり前提条件の検証は単一集約内で完結せず、集約をまたぐ。本プロジェクトの規約（[architecture.md](../../.claude/rules/architecture.md)）では、複数の集約をまたぐドメインロジックはドメインサービス（`service/` のトップレベル関数）の責務である。検証は `registerInStudBook` が担う。

ここで「生成口を誰に見せるか」が論点になった。`BloodHorse` のコンストラクタや生成ファクトリ（`of`）を `public` にすると、検証を経ずに `BloodHorse` を直接 `new` できてしまう。すると「血統登録済み（前提条件を満たした）個体」という不変条件が型で守られず、`registerInStudBook` を迂回した不正な生成が文法上可能になる。

対比として、騎手集約 `Jockey` は生成ファクトリ `Jockey.create` を `public` にしている。`Jockey` の不変条件（姓名がブランクでない）は**集約内で完結**するため、ファクトリ自身が検証すれば足り、生成口を隠す必要がない。`BloodHorse` との違いは「不変条件が集約内で閉じるか、集約をまたぐか」にある。

検討した代替案:

- **生成口を public のままにし、規約文書で「直接生成するな」と書く**: 文章のみのルールは強制力がなく、迂回をコンパイラが防げない。却下。
- **生成口を private にし、ドメインサービスを `BloodHorse` と同じパッケージ（同一ファイル）に置く**: パッケージプライベートで閉じられるが、ドメインサービスは `service/` リング、モデルは `model/` リングという本プロジェクトのパッケージ分割（オニオン 4 リング）と矛盾する。却下。
- **生成口を internal にする**: Kotlin の `internal` はモジュール（Gradle のソースセット単位）に閉じる。`model/` の `BloodHorse.of` を `internal` にすれば、同一モジュールの `service/` リングにある `registerInStudBook` からは呼べるが、別モジュール（将来切り出すアダプタ等）からは呼べない。採用。

`internal` を採ると、テストから生成口をどう叩くかが次の課題になる。テストでは前提条件検証を経ずに任意の `BloodHorse` を用意したい（例: `registerInStudBook` へ渡す父・母そのもの）。`internal` はモジュール内に見えるため、**test ソースセットも同一モジュールであれば直接 `of` を呼べる**。これを Object Mother パターン（`BloodHorseFixture`）に集約する。

## Decision（決定）

- 集約の生成口の可視性は、その集約の**不変条件が集約内で完結するか、集約をまたぐか**で決める。
  - **集約内で完結する不変条件のみ**を持つ集約は、検証ファクトリ（`create` 等）を **`public`** にする（例: `Jockey.create`。姓名ブランク検証は集約内で閉じる）。
  - **集約をまたぐ前提条件**を満たして初めて生成してよい集約は、生成口（`of` 等）を **`internal`** にし、前提条件を検証するドメインサービス（同一モジュールの `service/`）からのみ呼べるよう封じ込める（例: `BloodHorse.of` を `registerInStudBook` に封じ込める）。これにより「検証済みでなければ生成できない」を**型と可視性で**担保し、サービスの迂回を文法的に不能にする。
- `internal` 生成口を持つ集約のテストは、生成口を直接叩く責務を **Object Mother（`〜Fixture`）に一点集約**する。test は同一モジュールなので `internal` が見え、`BloodHorseFixture` が `BloodHorse.of` を直接呼んで任意の馬を組み立てる。個々のテストはモックを使わず、Fixture が返す実物の集約で検証する（[testing.md](../../.claude/rules/testing.md) の Fixture 方針に沿う）。Fixture は対象コンテキストの `model` パッケージ配下に test コードとして置く。

守るべきルールの結論は [CLAUDE.md](../../CLAUDE.md)「Entity パターン」「Value Object パターン」および [architecture.md](../../.claude/rules/architecture.md) に置く。参考実装は `domain/horseracing/model/horse/bloodhorse/BloodHorse.kt`（`of` が internal）、`service/horse/RegisterInStudBook.kt`（唯一の正規生成経路）、`test` の `BloodHorseFixture`。経緯は issue #268 / #266、PR #263。生成口の封じ込めは [ADR-0009](0009-immutable-aggregates.md)（イミュータブル集約・private constructor）と組み合わさり、`BloodHorse` の生成・写像経路を完全に統制する。

## Consequences（結果・影響）

- 「血統登録済みの `BloodHorse`」という不変条件が、文章ルールではなく**型と `internal` 可視性**で守られる。`registerInStudBook` を迂回した生成はコンパイル時に不能になる。
- 生成口を隠すか公開するかの判断基準が「不変条件が集約内で完結するか」に統一され、新しい集約（種牡馬・繁殖牝馬・競走馬などのロール統一: issue #266）を追加するときも同じ基準で判断できる。集約内で閉じるなら `public create`、集約をまたぐ前提が要るなら `internal of` ＋ ドメインサービス封じ込め。
- `internal` はモジュール（ソースセット）境界でしか閉じないため、**将来アダプタ等を別モジュールに切り出すと自然に生成口が隠れる**一方、現状は test を含む同一モジュール全体から見える。テストが生成口を叩けるのはこの可視性に依存しており、Object Mother に集約することで「どこから internal を叩いているか」を一箇所に局所化している。Fixture 以外から直接 `of` を呼ばないこと。
- Object Mother 経由のテスト構築は、Fixture にデフォルト値を集約することでテストの記述量を抑えられる反面、Fixture が肥大化しうる。Fixture の置き場・ソースセット方針の確定は issue #326 で別途扱う。
- 父母不明個体（輸入馬・基礎輸入馬）の登録経路（issue #267）では「父母 `BloodHorse` を必須とする」前提が崩れるため、生成口の引数（現状 `sireId` / `damId` 必須）の見直しが必要になる。その際も「生成口は検証済みドメインサービスからのみ」という本決定の骨子は維持する。
