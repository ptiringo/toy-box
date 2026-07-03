# 0051. トランザクション境界は application 層ユースケースの宣言的 @Transactional で置く

- Status: Accepted
- Date: 2026-07-03
- Deciders: Matsui

## Context（背景・課題）

血統登録系ユースケースは審査（`HorseInspection`）と軽種馬（`BloodHorse`）の 2 集約を連続 save しており、トランザクション境界が無かった（#312 で入ったプロジェクト初の複数集約書き込み）。業務ルール却下時の孤児は「成功時のみ save」で排除済みだが、2 番目の save がインフラ障害で失敗すると先行した審査 save が孤児として残る（失敗時原子性の欠落。#483）。

一方、オニオン規約（ArchUnit `applicationDependsOnSpringOnlyForDi`）は application 層の Spring 依存を `org.springframework.stereotype` のみに制限しており、`@org.springframework.transaction.annotation.Transactional` の直付けは違反だった。

## Decision（決定）

**書き込みユースケース（`Command` を受ける `invoke`）に宣言的 `@Transactional` を付与し、ユースケース＝トランザクション境界とする。** これを成立させるため、application 層の Spring 依存許可へ `org.springframework.transaction.annotation..`（アノテーションパッケージのみ）を追加する。

- 緩和は「宣言的メタデータのみ許可」という stereotype と同型の線引きであり、`TransactionTemplate` / `PlatformTransactionManager` 等の実行機構への依存は引き続き禁止する。
- 付け忘れは ArchUnit `commandHandlingInvokesAreTransactional`（`Command` を受ける `invoke` は `@Transactional` 必須）で構造的に防ぐ。読み取り系（`〜Query` 入力）は対象外（readOnly トランザクションは YAGNI で導入しない）。
- ユースケースは例外でなく `Result` を返す規約だが、業務失敗（`Err`）時は save に到達しない設計のため「実行時例外でロールバック」という `@Transactional` の既定と整合する。インフラ障害は `DataAccessException`（RuntimeException）として伝播しロールバックを引き起こす。
- Virtual Thread + ブロッキング JDBC 構成（ADR-0002）で宣言的トランザクションは素直に機能する（接続のスレッド束縛は ThreadLocal ベースで、Virtual Thread でも機能する）。`kotlin("plugin.spring")` により `@Service` クラスは open で CGLIB プロキシが効く。

## Alternatives（検討した代替案）

- **Tx ポート抽象（`TransactionRunner` を application が受け、infrastructure が `TransactionTemplate` で実装）**: application 層の純度は保てるが、間接層が増え全ユースケースにブロックネストが入る。将来の publish-after-commit（#433）で `TransactionSynchronization` 相当の概念がポートへ漏れがち。不採用。
- **adapter 層（controller / デコレータ）に境界を置く**: HTTP アダプタとトランザクションの関心が混在し、MCP 等の別アダプタ経由の呼び出しで境界が消える。不採用。

## Consequences（帰結）

- 審査＋軽種馬の 2 集約書き込み（`RegisterInStudBookUseCase` / `RegisterFoalUseCase` / `RegisterImportedHorseUseCase`）はインフラ障害時も原子的になる。ロールバックは統合テスト（`RegisterHorseTransactionRollbackTest`、Testcontainers PostgreSQL）で検証する。
- ドメインイベント発行基盤（#433 / [ADR-0029](0029-domain-events-via-state-transition-return.md)）が想定する publish-after-commit（`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`）は「発行時点でアクティブなトランザクションが存在する」ことを前提とし、本決定はその前提をそのまま満たす。
- 同一トランザクションで触る集約が増えるほど楽観ロック競合（[ADR-0047](0047-aggregate-version-for-optimistic-locking.md)）の面は広がる。「1 トランザクション 1 集約」の古典則は、集約境界を疑う設計圧力としては引き続き意識する（本プロジェクトは単一 DB のモジュラーモノリスであり、結果整合性・saga の複雑さを払う理由がない）。
