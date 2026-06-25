# 0030. 永続化実装を JDBC 一本に統一し、InMemory リポジトリを廃止する（datasource を H2↔PostgreSQL で差し替える）

- Status: Accepted
- Date: 2026-06-25
- Deciders: Matsui

## Context（背景・課題）

[ADR-0027](0027-persistence-spring-data-jdbc.md) で永続化アクセスに Spring Data JDBC を主軸採用し、その移行過程として次の 2 つの sub-decision を置いていた。

1. **高速ユニット用に InMemory リポジトリ（`ConcurrentHashMap`）を残す**（ADR-0027 Decision 4）。
2. **InMemory↔JDBC をプロファイルで切り替える本番配線**（ADR-0027 次アクション (4)。#423 のスコープ）。

#423 の実装に着手して、この前提を見直した。

- **InMemory が買っている価値が小さい**。当初は「高速ユニット用」と位置づけたが、実際にはアプリケーション層テストは Repository ポートを mockk でスタブし（[testing.md](../../.claude/rules/testing.md)）、ドメイン層テストは Fixture で実物を組むため、**InMemory 実体を使うテストは無い**。InMemory の唯一の実利用は「外部 DB なしでアプリを起動させる」ことだった。
- **その仕事は H2 が既に担える**。ADR-0027 / #422 で datasource は組み込み H2（PostgreSQL 互換モード）に固定済みで、Flyway も起動時に走る。つまり JDBC 実装は datasource=H2 のまま外部 DB なしで起動できる（Cloud Run / Container Smoke Test もこの経路で動く）。Cloud Run のデータは元から揮発なので、InMemory→JDBC on H2 で挙動の後退はない。
- **二重実装の保守コストが高い**。集約ごとに InMemory と JDBC の 2 実装を持ち、プロファイルで排他にする構成は、集約が増えるほど保守が二重になる。InMemory を残す動機が薄い以上、この複雑さは引き受けるに値しない。
- **プロファイル切替の前提が崩れる**。InMemory を畳むなら「InMemory↔JDBC」という切替先が消えるため、リポジトリ実装をプロファイルで差し替える機構（ADR-0027 次アクション (4) で想定した `@Profile` 配線）は不要になる。残すべき切替は**実装ではなく datasource（H2↔PostgreSQL）**であり、これは Spring 標準の environment / 環境変数で差せる。

### 検討した代替案

- **(却下) ADR-0027 のままプロファイルで InMemory↔JDBC を切り替える**: 上記のとおり InMemory の価値が小さく、二重実装とプロファイル機構の複雑さに見合わない。
- **(却下) InMemory を「fast fake」としてテスト専用に残す**: 現状テストはポートを mockk するため不要。将来、契約テストより速いポートのフェイクが要るなら、その時点でテストフィクスチャとして導入すればよく、main コードに常設する必要はない。

## Decision（決定）

**永続化実装は JDBC（Spring Data JDBC）一本に統一する。** ADR-0027 の主軸決定（Spring Data JDBC / PostgreSQL / Flyway / Testcontainers）はそのまま有効とし、その上で次の 2 点を ADR-0027 から改める（本 ADR が該当 sub-decision を supersede する）。

1. **InMemory リポジトリ（`ConcurrentHashMap` 実装）を廃止する。** ポートの実装は集約ごとに JDBC 1 本のみとする。
2. **リポジトリ実装のプロファイル切替（`@Profile`）は導入しない。** 起動 datasource を **H2(dev / Cloud Run) ↔ PostgreSQL(本番)** で差し替える（`application.yml` のデフォルトは H2、本番は環境変数で上書き）。切り替えるのは datasource であって実装ではない。

### 段階的移行

JDBC 実装があるのは現状 `Jockey` のみ。残り集約（`BloodHorse` / `BreedingRegistration` / `BreedingResult`）は JDBC 実装が未整備のため、**集約ごとに「JDBC 実装＋ Flyway マイグレーション＋契約テストを追加し、その集約の InMemory を削除する」を 1 セット**で進める（#435）。本 ADR を受けた最初の一歩として `Jockey` の InMemory を廃止し、`JdbcJockeyRepository` を唯一の実装にする（#423）。全集約の移行が完了した時点で InMemory は main から消える。

## Consequences（結果・影響）

- **良くなる点**: 集約ごとの実装が 1 本になり二重保守が消える。dev（H2）と本番（PostgreSQL）が**同一の JDBC コードパス**を通り、差は datasource 設定だけになる。H2↔PostgreSQL の方言差は Testcontainers 契約テストが実 PostgreSQL で検証するため、コードパスの一本化と方言検証が両立する。
- **引き受けるトレードオフ**:
  - ランタイムのデフォルト datasource として **H2 を main 実行時クラスパスに残す**ことになる（ADR-0027 の「H2 は test スコープへ寄せる」という想定アサイドは本 ADR で取り下げる）。組み込み H2 は軽量で、Cloud Run の揮発前提とも整合するため許容する。
  - 起動時に必ず Flyway マイグレーションが走る（`ConcurrentHashMap` 初期化より重いが無視できる範囲）。
  - 「外部 DB を一切初期化せず起動する」経路は無くなる（H2 は組み込みのため外部依存ではない）。
- **本番 PostgreSQL 化との関係**: 実データを永続化する本番 PostgreSQL（Cloud SQL 新設・Cloud Run への配線）は引き続き別作業（インフラ）に委ねる。本 ADR はアプリ側の実装一本化と datasource 差し替え方針までを定める。
- **結論の所在**: 「永続化実装は JDBC 一本／InMemory は持たない／datasource を H2↔PostgreSQL で差す」は `.claude/rules/testing.md` と `application.yml` のコメントに反映する。
- **関連**: 主軸決定は [ADR-0027](0027-persistence-spring-data-jdbc.md)（本 ADR がその InMemory 保持・プロファイル切替の sub-decision を supersede）。実装の段階移行は #423（Jockey）/ #435（残り集約）。イミュータブル集約・value class ID・UUIDv7 外部採番の制約は [ADR-0009](0009-immutable-aggregates.md) / [ADR-0005](0005-time-based-uuid-generation.md)。テスト方針は [ADR-0015](0015-gradle-build-performance-tuning.md) と testing.md。
