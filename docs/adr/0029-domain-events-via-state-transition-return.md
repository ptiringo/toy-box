# 0029. イミュータブル集約のドメインイベントは状態遷移の戻り値に同梱して収集する

- Status: Accepted
- Date: 2026-06-24
- Deciders: Matsui

## Context（背景・課題）

DDD ビルディングブロックを jMolecules のアノテーション（`@AggregateRoot` / `@ValueObject` / `@Repository` / `@field:Identity`）で表明する方針を採っているが、**ドメインイベントだけが一切使われていなかった**（issue #330）。ドメインには「起きたこと」の瞬間がすでに明確にある（血統登録・馬名命名・繁殖成績報告など）一方で、`@DomainEvent` を欠いたままなのは不自然だった。現状あるのは `Command<T>`（発生時刻を添える「何をしたいか」の入力の封筒）のみで、これは「何が起きたか」ではない。

そこで**最小スコープで参考実装を 1 本作り、本プロジェクトに合うドメインイベントの収集・発行方式を確立する**こととした。設計上の最大の論点は、**イミュータブル集約（[ADR-0009](0009-immutable-aggregates.md)）との整合**である。

Spring / jMolecules 定番の `AbstractAggregateRoot.registerEvent()` は、集約内部のイベントバッファを**ミュータブルに溜める**イディオムで、`val` のみ・状態遷移は新インスタンスを返す本プロジェクトの方針と噛み合わない。イミュータブル集約に合うイベント収集方式を決める必要があった。検討した案:

- **(A) 戻り値にイベントを同梱**: 状態遷移メソッドが「遷移後の集約とイベントの組」を返す。集約は純粋なまま、発行は application 層が担う。
- **(B) 集約がイベントリストを保持**: 新インスタンスに `pendingEvents: List<DomainEvent>` を持たせ、`copy` で引き継ぐ。集約が「未発行イベント」という発行機構の都合を抱え込み、`copy` の引き継ぎ漏れリスクも増える。

## Decision（決定）

**案 (A) を採用する。** イミュータブル集約の状態遷移メソッドは、遷移後の集約と発生したドメインイベントを **`StateTransition<A, E>`（`domain.shared`）に同梱して返す**。失敗しうる遷移は `Result<StateTransition<A, E>, エラー>` を返し、**失敗時はイベントを生成しない**。

- 参考実装は馬名命名: `BloodHorse.assignName(horseName): Result<StateTransition<BloodHorse, HorseNamed>, HorseAlreadyNamed>`。成功時のみ命名済みの新 `BloodHorse` と `HorseNamed` を返す。
- ドメインイベント型（`HorseNamed`）は **`@org.jmolecules.event.annotation.DomainEvent` で役割を表明**し、他のビルディングブロックと同じく `domain.*.model` に置く。他集約への参照は ID 値クラス経由（`BloodHorseId`）とする。集約と違い `data class` を使ってよい（値としての等価性が自然で、ID ベース `final equals` を持つ集約のような衝突がない）。
- **イベントの発行は application 層が担う**。ユースケースが `StateTransition` を受け取り、`aggregate` を永続化し、`event` を扱う。現状の最小ハンドリングは**ログ出力のみ**に留める。
- ドメインイベントを **DDD ビルディングブロックの一員**として扱う。ArchUnit の配置ルール（`dddBuildingBlocksResideInDomainModel`）と、ユビキタス言語の型レベルカタログ（`UbiquitousLanguageCatalogTest`）に `@DomainEvent` を加える。

守るべきルールの結論は [CLAUDE.md](../../CLAUDE.md)「ドメイン駆動設計」と [.claude/rules/architecture.md](../../.claude/rules/architecture.md) に置く。

### スコープ外（別イシュー送り）

最小スコープを保つため、以下は本決定に含めず別途とする:

- Spring `ApplicationEventPublisher` / `@EventListener` への連携。
- 永続化と整合した **publish-after-commit** のトランザクション意味論（永続化層が InMemory の現状ではフルに示せない）。
- イベントへの**発生時刻・イベント ID 等のメタデータ付与**（enrichment）。当面イベントは純粋なペイロードのみとし、「いつ」は application 境界の `Command.issuedAt` が持つ。将来は `Command<T>` と対称な封筒で包む余地がある。
- 複数イベントを返す遷移（現状 1 遷移 = 1 イベント。必要になれば `StateTransition` の `event` を集合へ拡張する）。
- 他集約・他コンテキストへの横展開（繁殖成績報告 #265 等）。

## Consequences（結果・影響）

- 集約は純粋（`val` のみ・副作用なし）なまま、状態遷移と同時に「起きたこと」を表現できる。発行機構（バッファ・publisher）の都合を集約に持ち込まずに済む。
- イベントの発行タイミングを application 層が一元的に握れるため、将来 publish-after-commit やパブリッシュ先の差し替えを集約に触れず導入できる。
- 状態遷移メソッドの戻り値が `Result<集約, エラー>` から `Result<StateTransition<集約, イベント>, エラー>` へ変わるため、呼び出し側は `.aggregate` を取り出す必要がある（既存の `assignName` 呼び出しと、その単体テストを更新した）。
- `StateTransition` は単一イベント前提の薄い封筒であり、複数イベントや遅延発行が必要になった時点で拡張する。最小スコープで「方式を 1 つ確立する」という目的に対する割り切り。
