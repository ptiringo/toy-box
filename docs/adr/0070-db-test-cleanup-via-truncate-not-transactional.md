# 0070. DB を触るテストの後始末は基底クラスの TRUNCATE に一元化し、@Transactional 分離は採らない

- Status: Accepted
- Date: 2026-08-02
- Deciders: ptiringo

## Context（背景・課題）

永続化の契約テストは `@SpringBootTest(NONE)` ＋ 各クラスの `@BeforeEach { deleteAll }` で DB 状態を戻していた。PR [#439](https://github.com/ptiringo/toy-box/pull/439)（軽量 CQRS の読み取り経路）のレビューで「`@Transactional`（テストごとの自動ロールバック）によるテスト分離へ寄せるべきか」が論点になり、[#440](https://github.com/ptiringo/toy-box/issues/440) として評価が積まれた。

#440 のコメントは `JdbcBloodHorseQueriesContractTest` の flaky を「テスト間干渉の兆候かもしれない」として記録していたが、この flaky は [#683](https://github.com/ptiringo/toy-box/issues/683)（コミット 987955c）で別原因として決着している。`generateId()`（`Generators.timeBasedEpochRandomGenerator()`）が同一ミリ秒内での単調増加を保証しないことが原因で、テストを固定 UUID ベースへ書き直して解消した。したがって**テスト間干渉を支持する観測は存在しない**。

一方で、クリーンアップそのものには実在の穴が 3 つあった。

1. `NameHorseUseCasePublishAfterCommitTest` が `horse_inspection` / `blood_horse` に行を書くのに後始末をしていない。固定 `registrationNumber = "2020900123"` の行が共有コンテナに残り続ける（[#652](https://github.com/ptiringo/toy-box/issues/652) で UNIQUE を張った時点で壊れる）
2. クリーンアップ対象テーブルが `StudbookTables.kt` の手書きリストで、マイグレーションに自動追従しない
3. 方式が揃っていない。studbook は `deleteAllStudbookTables(jdbcClient)`、racing は `rows.deleteAll()`（自テーブルのみ）

### スパイクによる実測（2026-08-02 実施）

推論で決めないよう、`@Transactional` を実際に付けて走らせた。契約テスト 3 クラス（`JdbcBloodHorseRepositoryContractTest` / `JdbcBloodHorseQueriesContractTest` / `JdbcJockeyRepositoryContractTest`）へ `@Transactional` を付けて後始末を全削除し、あわせてトランザクション意味論を検証する 2 クラス（`RegisterHorseTransactionRollbackTest` / `NameHorseUseCasePublishAfterCommitTest`）にも付けた。スパイクは破棄済み。

| 検証項目 | 実測結果 |
|---|---|
| `@Version` インクリメント・楽観ロック（`UpdateConflict`） | `@Transactional` 下でも全て通る |
| CHECK / FK / UNIQUE の拒否（`DataIntegrityViolationException` 期待の 4 本） | 通る（制約は即時検査のため tx 内でも発火） |
| 読み取り経路（`JdbcClient` 直 SELECT） | 通る（同一 tx の未コミット行を読める） |
| 自分の書き込みの隔離 | 後始末コードを全削除しても 3 クラス単独実行は 22/22 green |
| トランザクション意味論の 2 クラス | 4/4 FAILED。`AFTER_COMMIT` は届かず（`List is empty`）、UseCase の `@Transactional` は外側テスト tx に join してロールバックが効かない（孤児が残る） |
| 混在実行（部分適用） | 76 中 10 FAILED。非トランザクションのクラスがコミットしたゴミを、後始末を外した `@Transactional` クラスが拾う |
| コンテキストキャッシュ | 生成コンテキスト数は 3 で baseline と同一（`@Transactional` はキーを分けない） |
| 速度 | テスト本体で約 0.45 秒短縮（0.94s → 0.49s） |

#440 が挙げていた懸念（`@Version` の挙動差・可視性の隠蔽・コンテキストキャッシュの増加）は**いずれも空振り**だった。Spring Data JDBC は JPA と違い永続化コンテキストを持たず `save()` が即 SQL を発行し、FK は全て `NOT DEFERRABLE`（`V11__add_aggregate_reference_foreign_keys.sql`）、UNIQUE も即時であるため、コミット時に遅延検査される制約は 1 つも存在しない。

## Decision（決定）

**`@Transactional` によるテスト分離は採らない。契約テストは実コミットのまま、後始末を `PostgresContainerSupport` の `@BeforeEach` に一元化する。**

採らない理由は「ロールバックが実挙動を隠すから」ではなく、**適用範囲を 100% にできないから**である。

- トランザクション意味論を検証する 2 クラス（[ADR-0050](0050-domain-event-publication-after-commit.md) の publish-after-commit、[ADR-0051](0051-transactional-use-case-boundary.md) のユースケース Tx 境界）は実コミットが必須で、`@Transactional` 化すると検証対象そのものが消える（実測 4/4 FAILED）
- それらがコミットするゴミは共有コンテナに残るため、後始末機構は結局消せない
- さらに `@Transactional` 下では `@BeforeEach` の DELETE 自体もロールバックで巻き戻り、ゴミを恒久除去できない（毎回消し直しになる）
- 結果として `@Transactional` は「後始末を無くす」という主目的を達成できず、隔離方式が二重化するだけになる。#440 が求めた「write / read で方式を揃える」一貫性は、むしろ一元化側でしか達成できない

実装は次のとおり。

- `PostgresContainerSupport` に `@BeforeEach truncateAllTables()` を置く。**Testcontainers の JDBC URL へ Spring を介さず直接接続**し、`TRUNCATE ... CASCADE` を 1 文で発行する。Spring 非依存にするのは、テスト側の接続やトランザクション状態、`@SpringBootTest` の構成に後始末を依存させないため
- 対象テーブルは `pg_tables` から動的に列挙する。除外はシステムスキーマ（`pg_catalog` / `information_schema`）と Flyway の内部管理テーブル `flyway_schema_history`（[ADR-0048](0048-per-context-db-schema-namespaces.md) の方針で既定スキーマに残る）だけ。スキーマ名もテーブル名もハードコードしない
- 全テーブルを 1 文でまとめて TRUNCATE するため、FK の依存順（[ADR-0053](0053-foreign-key-backstop-across-aggregates.md)）を考える必要が無い
- 列挙が空なら `check` で落とす。空振りしても `TRUNCATE` は無言で成功し「後始末しているつもり」になるため

## Consequences（結果・影響）

- 後始末の書き忘れが構造的に起きなくなる。`PostgresContainerSupport` を継承した時点で必ず効くため、テスト側にクリーンアップを書く必要が無い。上記の穴 1（`NameHorseUseCasePublishAfterCommitTest` の漏れ）はこれで塞がった
- 手書きの `StudbookTables.kt` を削除した。マイグレーションでテーブルが増えても手で追従する必要が無い
- studbook / racing で割れていた方式が 1 つになった
- `src/e2eTest`（`JockeyApiE2eTest`）と `src/replay`（`BreedingReplayTest` / `ReplayStopBranchTest`）も同じ基底クラスを継承しているため後始末を得る。replay 側は自前の `cleanUp` を削除した
- DB を触らないテスト（`HealthEndpointTest` / `OpenApiTest` / `SecurityConfigTest` / `McpServerWiringTest` / `ApiApplicationTests`）でも TRUNCATE が走るが、コストは接続 1 本 + 2 クエリ / テストにとどまる
- トレードオフとして、テストを読んだだけでは DB が空である理由が見えなくなる（基底クラスの KDoc で補う）
- 後始末そのものは `PostgresContainerSupportTest` が検証する。列挙が空でないこと・両スキーマを網羅すること・内部管理テーブルを含まないことに加え、前のテストが残した行が次のテストの開始時に消えていることを実行順を固定して確かめる
- テスト並列化を再評価する際（[#338](https://github.com/ptiringo/toy-box/issues/338)）は、この共有コンテナ + TRUNCATE 方式が JVM 内並列と両立しない点（他スレッドのデータまで消す）を前提に設計し直す必要がある
