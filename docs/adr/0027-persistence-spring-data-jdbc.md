# 0027. 永続化アクセスに Spring Data JDBC を主軸採用し、PostgreSQL / Flyway / Testcontainers で構成する

- Status: Accepted
- Date: 2026-06-24
- Deciders: Matsui

## Context（背景・課題）

現状このプロジェクトは永続化層を持たず、Repository ポート（`domain.*.model` の interface）の実装は `infrastructure` 層の **InMemory（`ConcurrentHashMap`）** が 4 本あるのみ（`InMemoryJockeyRepository` / `InMemoryBloodHorseRepository` / `InMemoryBreedingRegistrationRepository` / `InMemoryBreedingResultRepository`）。永続化系の依存はゼロで、アプリ再起動でデータは消える。

本物の永続化に着手するにあたり、**アクセス技術・DB・マイグレーション・テスト戦略を決めて記録する**ための意思決定。実装は本 ADR の結論を受けて別イシューにブレイクダウンする（#338）。本 ADR は調査結果に基づくドラフトであり、結論前の小さな spike（後述）で検証してから `Accepted` へ昇格させる。

### 前提制約（既存 ADR がそのまま効く）

選択肢はプロジェクト固有の制約でかなり絞られる。

- **Virtual Thread + ブロッキング IO**（[ADR-0002](0002-virtual-thread-over-reactive.md)）→ R2DBC / リアクティブは対象外。**JDBC 一択**。
- **イミュータブル集約**（[ADR-0009](0009-immutable-aggregates.md)）: `val` only / `private constructor` + 手書き `copy` / ID ベースの `final equals`・`hashCode`。`data class` は使わない。
- **value class の ID**（`@JvmInline value class JockeyId(val value: UUID)`、[ADR-0014](0014-self-validating-factory-over-confinement.md) の生成口）。
- **UUIDv7 の外部採番**（[ADR-0005](0005-time-based-uuid-generation.md)）。アプリ側（`domain.shared.generateId()`）で採番して渡す。DB 採番ではない。
- **オニオン**（[architecture.md](../../.claude/rules/architecture.md)）: ポートは domain、実装は infrastructure。**集約 = 永続化の境界**。

→ これらにより **JPA / Hibernate は実質脱落**する。mutable entity・no-arg constructor・proxy・遅延ロードを前提とする JPA は、イミュータブル集約（`val` only / private constructor）・value class ID と正面衝突する。

### 検討した代替案

| 候補 | 相性 | 評価 |
|------|------|------|
| **Spring Data JDBC** | ◎ | 集約指向で DDD アグリゲートが永続化単位。遅延ロード／ダーティトラッキングを持たず、イミュータブル集約と噛み合う。Spring ネイティブで `@Version` による楽観ロック・採番判定が素直（後述）。**主軸に採用** |
| **jOOQ** | ○ | 型安全 SQL・魔法なし。複雑な読み取り／Read Model（#293 の軽量 CQRS）には強い。書き込みは手数が増え、集約マッピングは手書き。**将来の複雑クエリに併用余地を残す** |
| Exposed | △ | Kotlin ネイティブだが Spring 統合が薄く、集約境界の表現は手薄。却下 |
| JPA / Hibernate | ✕ | 上記の通りイミュータブル集約・value class と衝突。却下 |

- **(却下) jOOQ 単独**: 書き込み（集約の save）まで全部手書き SQL になり、現状の単純な集約（Jockey / BloodHorse）には過剰。集約 write は Spring Data JDBC に任せ、必要になったら read だけ jOOQ を足すのが費用対効果が高い。
- **(却下) JPA**: 上表の通り。イミュータブル方針（ADR-0009）を曲げてまで採る理由がない。

### 検討で潰した落とし穴

Spring Data JDBC を採るうえで、本プロジェクト固有の制約と衝突しうる 3 点を調査した。

1. **value class ID のマッピング**: `@JvmInline value class JockeyId(val value: UUID)` ↔ DB の `uuid` 列。Spring Data JDBC は value class を自動では解さないため、**カスタムコンバータ**（`Writing/ReadingConverter`、ID ↔ `UUID`）を `JdbcCustomConversions` に登録して橋渡しする。Kotlin の inline value class はメソッド名マングリングの絡みで素の自動マッピングが効きづらいため、コンバータ経由を前提とする（spike で実挙動を確認する）。
2. **外部採番時の insert / update 判定** ＆ 3. **イミュータブル × 楽観ロック**: この 2 つは **`@Version` 列で一石二鳥に解ける**ことを確認した（Spring Data 公式）。
   - Spring Data JDBC は本来「`@Id` が null か」で新規／更新を見分けるが、UUIDv7 を外部採番すると ID は常に非 null になり、そのままでは全件 update 扱いになる。
   - `@Version` 数値列を集約に持たせると、**version が null / 0 のとき新規 insert** と判定される（採番済み ID でも正しく insert される）。save 後に version がインクリメントされ「新規でない」状態へ遷移する。これは「ID をオブジェクト構築時に採番する（UUID）」ケースのために用意された公式の解法。
   - 同じ `@Version` 列が楽観ロック（update 時に `WHERE version = ?` を付け、不一致なら `OptimisticLockingFailureException`）も兼ねる。
   - イミュータブル方針との折り合い: version も `val` のコンストラクタ引数として集約に持たせ、`save()` が返す**インクリメント済みの新インスタンス**を呼び出し側が受け取る（`save` が永続化済みインスタンスを返す現行ポート契約と整合）。version はドメインの不変条件ではなく永続化メタデータなので、集約に持たせる是非は実装イシューで最終確認する（別 VO / 別経路の余地も残す）。

## Decision（決定）

**永続化アクセスの主軸に Spring Data JDBC を採用し、DB は PostgreSQL、マイグレーションは Flyway、テストは Testcontainers で構成する。** 具体的には:

1. **アクセス技術**: 集約の write（および単純な read）は **Spring Data JDBC**。複雑な読み取り／Read Model（#293）が必要になった時点で **jOOQ を read 専用に併用**する余地を残す（初手では入れない）。
2. **DB**: **PostgreSQL**。
3. **マイグレーション**: **Flyway**（plain SQL）。明示的・逐次適用の趣味に合う。Liquibase（XML/YAML DSL 抽象）は採らない。
4. **テスト戦略**: **Testcontainers**（PostgreSQL コンテナ）で infrastructure 層の**契約テスト**を整備する（`.claude/rules/testing.md` の宿題「`infrastructure.*` の契約テスト未整備」に対応）。高速ユニット用に **InMemory リポジトリは残す**（ドメイン／アプリ層テストはこれまで通り Fixture + InMemory で回す）。テストのコンテキストキャッシュ方針（[ADR-0015](0015-gradle-build-performance-tuning.md)）は維持し、Testcontainers 利用テストの distinct なコンテキスト構成を増やさない。
5. **マッピング方式（spike で確定。当初案から補正）**: ドメイン集約には Spring Data のアノテーションを**付けない**。infrastructure 層に永続化モデル（`〜Row` data class）を別に置き、`@Id` / `@Version` / `@Table` / `@Column` はそこに付け、**ドメイン集約 ⇔ Row は手書きマッパーで相互変換**する。理由はオニオン規約（ArchUnit）が `domain..` の `org.springframework..` 依存を禁じており、`org.springframework.data.annotation.*` を集約に載せられないため（当初案の「集約に直接 `@Version`」「value class 用カスタムコンバータ」は採れない）。
   - **value class ID** → 別途の Spring Data カスタムコンバータは**不要**。Row は生の `UUID` 列を持ち、`JockeyId(uuid)` / `id.value` の変換は手書きマッパーが担う（永続化モデルを分離した帰結）。
   - **採番時の insert 判定** ＆ **楽観ロック** → **Row に** `@Version` 数値列を持たせ、「version null/0 = 新規」で insert を判定しつつ楽観ロックを兼ねる（`Persistable<ID>` 実装は採らない）。version はドメインへ漏らさない（ドメインは永続化メタデータを持たない）。
   - **再構成（リハイドレート）** → 集約に検証・採番を行わない復元用ファクトリ（例: `Jockey.reconstitute(id, …)`）を設け、マッパーはこれで Row から集約を組み立てる。`create`（新規・自己検証・採番）とは別口にする。

この決定は小さな spike（Jockey を Spring Data JDBC + H2 で 1 本通す）で確証を取って確定した。spike の結果は次節に記す。

## Spike 結果（2026-06-24）

`racing` の `Jockey` 集約を題材に、Spring Data JDBC + H2（インメモリ・`DATABASE_TO_LOWER`）で 1 本通す spike を実施した（実装は `infrastructure/racing/jockey` 配下と `JdbcJockeyRepositorySpikeTest`、検証は `db/migration/V1__create_jockey.sql` を `@Sql` で適用して実行）。`./gradlew check` が緑（ArchUnit / detekt / Kover 含む）であることまで確認した。

検証できたこと（いずれも上記テストで pass）:

- **マッピング方式は「集約に直接アノテーション」は不可、「別 Row + マッパー」が正**。ArchUnit のオニオン規約（`domain..` は `org.springframework..` 依存禁止）により、`@Id` / `@Version` を `Jockey` に載せると規約違反になる。そのため `JockeyRow`（infrastructure）に Spring Data アノテーションを閉じ込め、`JdbcJockeyRepository` アダプタが `Jockey` ⇔ `JockeyRow` を写像する構成にした。**当初案（集約に `@Version`／value class 用カスタムコンバータ）は撤回**。
- **value class ID は Spring Data コンバータ不要**。Row が生 `UUID` を持ち、マッパーが `JockeyId(uuid)` / `id.value` で変換すれば足りる。
- **外部採番 + `@Version` の insert/update 判定が機能**。外部採番した UUIDv7 を `@Id` に与えても、`@Version` が null の行は insert され、update では version がインクリメントされることを確認（落とし穴②③が解ける）。
- **再構成口が必要**。`Jockey` は ID を自己採番していたため、保存済み状態の復元用に `Jockey.reconstitute(id, firstName, lastName)`（検証・採番なし）を追加した。`create`（自己検証・採番）とは別口。

積み残し（follow-up）:

- **Flyway の自動実行が Spring Boot 4.1 + Flyway 12 で動かない**。`flyway-core` を入れ `spring.flyway.enabled=true` でもマイグレーションが走らず DB が空のままになる事象を確認（起動ログに Flyway 出力なし）。spike では `@Sql` でスキーマを用意して回避した。Flyway 12 の DB モジュール分割（`flyway-database-postgresql` 等）や Boot 4 の autoconfig 条件を要追検証。
- **更新系（version を進める save）はドメイン経由では未対応**。ドメインが version を持たないため、アダプタの `save` は常に insert になる。update は「version を Row 取得して引き当てる」等の方針を実装イシューで決める。
- **H2 は spike 用の暫定**。本番は PostgreSQL + Testcontainers（Docker 必須のため spike 環境では未実行）。識別子の大文字小文字など方言差は Testcontainers で詰める。
- **本番配線**: `JdbcJockeyRepository` は既存 `InMemoryJockeyRepository` との DI 衝突を避けるため spike では Bean 化していない。プロファイル分け等の配線は別イシュー。

## Consequences（結果・影響）

- **良くなる点**: 永続化が DDD アグリゲート単位（集約 = 永続化境界）で素直に表現でき、イミュータブル集約・value class ID・UUIDv7 外部採番という既存制約を曲げずに載せられる。永続化モデルを別 Row に分離したことで**ドメインは Spring 非依存のまま**（オニオン規約と両立）、value class 用コンバータも不要。`@Version` 一本で「採番済み ID の insert 判定」と「楽観ロック」を同時に解け、`Persistable` 実装のボイラープレートを避けられる。
- **引き受けるトレードオフ**:
  - 集約と Row の二重定義＋手書きマッパーが集約ごとに要る（その代わりドメインの純度を保てる）。
  - 複雑な read を Spring Data JDBC だけで賄うのは苦しく、いずれ jOOQ 併用（依存追加・二刀流の学習コスト）を引き受ける可能性がある。
  - Flyway 自動実行の配線に Boot 4.1 固有の追作業が要る（上記 follow-up）。
- **新規依存（spike で追加）**: `spring-boot-starter-data-jdbc`、`flyway-core`、`runtimeOnly` の H2（spike 用の暫定組み込み DB）。本番化時に PostgreSQL ドライバ＋テストの `spring-boot-testcontainers` / `testcontainers:postgresql` を足し、H2 を test スコープへ寄せる。version catalog（`gradle/libs.versions.toml`）への移管も実装イシューで行う。devcontainer の DinD（#302 / #331）が Testcontainers にそのまま効く。
- **次アクション（#338 のブレイクダウン）**: spike は完了。残りを実装イシューへ分解する — (1) Flyway 自動実行の配線（Boot 4.1 + Flyway 12）、(2) 本番 DB を PostgreSQL + Testcontainers 契約テストへ、(3) update/楽観ロックのドメイン経由対応、(4) 本番 Bean 配線（プロファイル）と残り集約（BloodHorse 等）の展開、(5) 依存の version catalog 移管。
- **関連**: 制約の典拠は [ADR-0002](0002-virtual-thread-over-reactive.md) / [ADR-0005](0005-time-based-uuid-generation.md) / [ADR-0009](0009-immutable-aggregates.md) / [ADR-0014](0014-self-validating-factory-over-confinement.md)。テスト方針は [ADR-0015](0015-gradle-build-performance-tuning.md) と `.claude/rules/testing.md`。Read Model 併用の動機は #293。親イシューは #338。
