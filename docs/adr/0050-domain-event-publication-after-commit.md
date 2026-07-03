# 0050. ドメインイベントは ApplicationEventPublisher で発行し AFTER_COMMIT で購読する

- Status: Accepted
- Date: 2026-07-03
- Deciders: Matsui

## Context（背景・課題）

[ADR-0029](0029-domain-events-via-state-transition-return.md) はイミュータブル集約のドメインイベント収集方式（状態遷移メソッドが `StateTransition<A, E>` で集約とイベントを同梱して返し、発行は application 層が担う）を確立したが、発行そのものは「ログ出力の最小ハンドリング」に留め、Spring `ApplicationEventPublisher` への連携と publish-after-commit のトランザクション意味論を明示的にスコープ外（別イシュー #433 送り）としていた。当時は永続化層が InMemory で実 DB トランザクションを示せなかったためだが、永続化の本番化（#422 / #423 / #451、[ADR-0030](0030-jdbc-only-persistence-retire-inmemory.md)）が完了し前提は解消した。

実装にあたり決めるべき論点が 3 つあった。

1. **application 層からの発行経路**: Spring の `ApplicationEventPublisher` を直接注入するか、発行ポート（interface）を切って infrastructure 層に委譲実装を置くか。オニオン規約では application 層の Spring 依存は DI 用 stereotype のみに制限されており（ArchUnit `applicationDependsOnSpringOnlyForDi`）、どちらの案でも規約側の判断が要る。
2. **トランザクション境界**: publish-after-commit（イベントは集約の永続化がコミットされた後にだけ届く）を成立させるには、発行元ユースケースにトランザクション境界が必要。現状リポジトリ全体で `@Transactional` は 1 件も無い。
3. **購読側の配置と意味論**: 参考実装リスナをどのリングに置くか。リスナ内例外がコミット済みトランザクションへ与える影響（ロールバック不可）の整理。

## Decision（決定）

### 発行経路: `ApplicationEventPublisher` を application 層へ直接注入する

発行ポートの抽象は導入せず、ユースケースが Spring の `ApplicationEventPublisher` をコンストラクタ DI で受け取り、永続化成功後に `publishEvent(transition.event)` する。

- `ApplicationEventPublisher` は関数 1 つの発行インターフェースで、これ以上に薄い自作ポートを重ねてもボイラープレートにしかならない（実装が Spring の 1 つしかあり得ない抽象は YAGNI）。
- プロセス内イベントの発行・購読・トランザクション同期は Spring のイベント基盤に乗り切る。将来 Spring Modulith（[ADR-0025](0025-defer-spring-modulith-adoption.md) で採用見送り・再評価余地あり）へ進む場合も、`ApplicationEventPublisher` 直接発行はそのまま Modulith のイベント externalization に接続できる。
- テスト容易性はポート抽象と同等（mockk で `publishEvent` を検証できる）。

これに伴い ArchUnit ルールを「application 層の Spring 依存は DI 用 stereotype のみ」から**精密 allowlist** へ更新する: `org.springframework.stereotype..`（DI）＋ `org.springframework.transaction.annotation..`（宣言的トランザクション境界）＋ `org.springframework.context.ApplicationEventPublisher`（クラス単位。イベント発行）。パッケージごと（`org.springframework.context..`）開けるのではなくクラス単位で許可し、`ApplicationContext` 等への依存が紛れ込む余地を塞ぐ。

### トランザクション境界: 発行元ユースケースに `@Transactional` を導入する

イベントを発行するユースケース（現状は `NameHorseUseCase` のみ）に `@Transactional` を付与し、「引当 → 状態遷移 → save → publishEvent」を 1 トランザクションに収める。`publishEvent` はトランザクション内で呼ばれるが、後述の `@TransactionalEventListener(phase = AFTER_COMMIT)` がイベントの配送をコミット確定まで遅延させるため、**コミットが成立した場合にのみ購読者へ届き、ロールバックされた場合は破棄される**（publish-after-commit）。

`@Transactional` の導入は本決定では発行元ユースケースに限る。複数集約書き込みの原子性のための全面展開は #483 が別途扱う。

### 購読側: リスナは infrastructure 層に置き、AFTER_COMMIT で同期実行する

参考実装リスナ（`HorseNamedLoggingListener`）は `infrastructure` 配下に置く。イベント購読は REST / persistence / MCP と並ぶアダプタの一形態であり、Spring 依存可のリングに置くのが自然（参考実装 1 本のために新しい adapter リングは切らない）。

```kotlin
@Component
class HorseNamedLoggingListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onHorseNamed(event: HorseNamed) { /* ログ出力 */ }
}
```

トランザクション意味論は以下のとおり整理する。

- **配送タイミング**: `AFTER_COMMIT` リスナはコミット確定後、発行元と同じスレッドで同期実行される（`@Async` は導入しない）。トランザクションがロールバックされた場合、イベントは配送されない。トランザクション外で `publishEvent` された場合は既定（`fallbackExecution = false`）で配送されない。
- **リスナ内例外はコミットを取り消せない**: リスナが走る時点でコミットは確定済みであり、例外を投げてもロールバックは起こらない。リスナの処理は「本体の書き込みと運命を共にしない」副作用（通知・ログ・キャッシュ更新等）に限り、失敗が業務的に許されない処理をリスナへ置いてはならない。なお AFTER_COMMIT リスナの例外は呼び出し元へ伝播しうる（同期実行のため）ので、リスナ側で捕捉・ログするのを基本とする。
- **at-least-once 配送は保証しない**: コミット直後のプロセスダウンでイベントは失われる。信頼性配送が要件化したら Outbox パターンを別途検討する（本決定のスコープ外）。

## Consequences（結果・影響）

- application 層の Spring 依存が「stereotype のみ」から「stereotype ＋ 宣言的 Tx ＋ イベント発行」へ広がる。いずれも DI・境界宣言・発行という**配線の語彙**であり、業務ロジックが Spring API に依存する形ではない。allowlist はクラス単位まで精密化して漏れ広がりを防ぐ（`.claude/rules/architecture.md` の依存表・ArchUnit `OnionLayerRulesTest` を同時更新）。
- 発行タイミングの意味論が「save が返ったら」から「コミットが確定したら」へ確定し、購読者が未コミットの状態変更を観測する余地がなくなる。実 PostgreSQL（Testcontainers）上の統合テストで、コミット後配送・ロールバック時破棄の両方を検証する。
- リスナに置けるのは失敗しても本体の書き込みに影響しない処理に限る、という設計制約を引き受ける（上記のとおり）。
- イベントのメタデータ enrichment（発生時刻・イベント ID の封筒、#434）、複数イベント遷移、Outbox / 外部メッセージングは引き続きスコープ外。
