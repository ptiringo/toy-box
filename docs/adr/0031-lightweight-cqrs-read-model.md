# 0031. 読み取りを集約非経由の Read Model 経路で実装する（軽量 CQRS / L2・レイヤー先）

- Status: Accepted
- Date: 2026-06-25
- Deciders: Matsui

## Context（背景・課題）

現状、application 層には書き込み系ユースケース（`JockeyRegistrationUseCase` / `RegisterInStudBookUseCase` 等）しか存在せず、読み取り経路が無い。読み取りを実装し始めるにあたり、何も方針を決めないと「クエリのたびに書き込み集約をリポジトリから復元して詰め替える」素朴な読み取りに倒れやすい。集約は不変条件・整合性境界・状態遷移を担う重い構造で、表示・参照のための読み取りにそのライフサイクルを通すのは過剰であり、表示要件が増えるほど集約 API が読み取り都合で歪む。

一方で本プロジェクトはサンドボックスであり、読み取り専用ストア・プロジェクション・結果整合・イベントソーシング（いわゆるフル CQRS = L3）を導入する段階にはない。書き込みと読み取りで**モデルと経路は分けたい**が、**ストアは分けない**——この中間（L2）をどう構造に落とすかを決める必要がある。

### 検討した構成案

- **パターン1（機能スライス先で command/query 分割）**: 現行のオニオン（レイヤー先 + コンテキストスライス）を機能凝集へ組み替える必要があり、既存構造との不整合が大きい。**却下**。
- **パターン2（レイヤー先・read を application の薄い query service として足す）**: 現行構成に最も素直に乗り、Spring + DDD 界隈で L2 lite のデファクト。**採用**。
- **パターン3（write/read を高位／別モジュールで分離）**: フル CQRS 向けで L2 には過剰。**却下**。

## Decision（決定）

**読み取り（クエリ）系を、書き込み側の集約を経由しない独立経路として実装する軽量 CQRS（L2）を採用する。** 読み書きでモデルと経路を分離するが、ストア分離・結果整合・イベントソーシング（L3）は採らない。構成は **パターン2（レイヤー先・薄い read 経路）** とする。

1. **Read Model（View）とクエリポートは `application` 層に置く。** 書き込みの「集約 + Repository ポートは `domain.*.model`」とは非対称だが、これは意図したもの。読みモデルは集約のライフサイクル（生成・状態遷移・整合性境界）を持たないため domain には置かず、ドメインを汚さない。
2. **View には jMolecules の `@QueryModel`（`org.jmolecules.architecture.cqrs`）を付ける。** 不変条件を持たないフラットな DTO で、値の等価性が自然なため `data class` でよい。「概念は jMolecules で表明し ArchUnit で機械強制する」現行の文化を CQRS へ延長する。依存は `jmolecules-cqrs-architecture`（BOM 管理）。
3. **クエリポート（`〜Queries`）は plain interface とし、書き込みポートの jMolecules `@Repository` は付けない。** 読み取りは Repository ビルディングブロックではない。
4. **infrastructure の実装は集約を組まずストアから直接 View へ詰める。** 書き込みの Row／集約／Spring Data リポジトリを経由しない。同じテーブルを読んでも、経路（write=集約復元 / read=View 直組み）とモデルを分離するのが L2 の価値であり、「write ポートに finder を生やす」誘惑には乗らない。
5. **読み取りも write と対称に `application` を必ず通す**（現行オニオンの依存方向を維持）。クエリ入力 DTO は `〜Query` サフィックス。書き込み系の `Command<T>` 封筒（発生時刻メタデータ）は読み取りでは使わない。

### リファレンス実装

`racing/jockey` で読み取り経路を 1 本通して型を確立する（以降はこれに倣う）。

- `application/racing/jockey/JockeyView.kt`（`@QueryModel` 付き Read Model）
- `application/racing/jockey/JockeyQueries.kt`（読み取りポート。plain interface）
- `application/racing/jockey/FindJockeyUseCase.kt`（`@Service`。`Result<JockeyView, JockeyNotFound>` を返す）
- `infrastructure/racing/jockey/JdbcJockeyQueries.kt`（`JdbcClient` で `jockey` を直 SELECT し View へ詰める）
- `controller/jockey`（`GET /api/jockeys/{id}`。`JockeyView` を書き込み経路と同一のリソース表現 `JockeyResponse` に詰める。ADR-0008）

### 機械強制

`@QueryModel` 付きクラスが `application` 配下に居ることを ArchUnit `queryModelsResideInApplication` で検証する（DDD ビルディングブロックを `domain.*.model` に縛る `dddBuildingBlocksResideInDomainModel` と対称）。読み取り経路が application を通ること（infrastructure → application のポート実装）は既存の `onionLayers` がアダプターの依存として許容する。クエリポートの命名・`@Repository` 非付与は意味的規約としてレビューで担保する（[ADR-0008](0008-uniform-resource-representation-response.md) のグルーピング判断・#307 と同種で、名前ベースの機械強制は過剰）。

## Consequences（結果・影響）

- **良くなる点**: 表示・参照のための読み取りが集約のライフサイクルから切り離され、集約 API が読み取り都合で歪まない。読みモデルは要件に合わせてフラットに最適化でき、書き込み側の不変条件と独立に進化する。
- **引き受けるトレードオフ**:
  - 同一データに対し write（集約）と read（View）の 2 モデルを持つため、列の追加等で両方の写像を触る局面が出る。L2 はストアを共有するため二重化は写像層に限られ、L3 のような同期・結果整合の複雑さは負わない。
  - read/write でポートの置き場所が非対称（write=domain / read=application）になる。これは「読みモデルは集約のライフサイクルを持たない」という性質の反映であり、architecture.md に明文化して意図を残す。
- **対象外（L3 / 別 issue）**: 読み取り専用ストア・プロジェクション・結果整合・イベントソーシング（L3）、パターン3（write/read の高位／別モジュール分離）、`Command<T>` 封筒の改名（`@Command` 注釈との名前衝突回避）。
- **結論の所在**: 守るべきルールは `.claude/rules/architecture.md`「読み取り経路（軽量 CQRS / L2）」と CLAUDE.md「Query パターン」に反映する。
- **関連**: リソース表現の単一化は [ADR-0008](0008-uniform-resource-representation-response.md)。jMolecules による役割表明・ArchUnit 強制の文化は [ADR-0007](0007-wire-enum-dto-decoupling.md) / [ADR-0009](0009-immutable-aggregates.md)。永続化の JDBC 一本化（read 実装も JDBC で組む前提）は [ADR-0027](0027-persistence-spring-data-jdbc.md) / [ADR-0030](0030-jdbc-only-persistence-retire-inmemory.md)。エラー描画（`JockeyNotFound` → 404）は error-handling.md / api-design.md。実装は #293。
