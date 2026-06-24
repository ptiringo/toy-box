# 0025. Spring Modulith は現時点では採用せず、永続化とコンテキスト間連携の実需要が出た時点で再評価する

- Status: Accepted
- Date: 2026-06-24
- Deciders: Matsui

## Context（背景・課題）

境界づけられたコンテキストが増え（studbook / racing / 将来の sakamichi / tennis）、ドメインイベントの最小導入（#330）を検討するのに伴い、モジュラモノリスを支援する公式ライブラリ **Spring Modulith** の採否を評価した（#355）。

Spring Modulith が提供する価値は主に 3 つ:

1. **モジュール間イベント** — `@ApplicationModuleListener` ＋ **Event Publication Registry** による、モジュール間の疎結合・非同期・信頼性のあるイベント連携（コミット後発火・失敗イベントの永続化と再送）。Modulith 最大の独自価値。
2. **境界検証** — モジュール間の循環なし・内部実装の隠蔽（named interface による公開 API の明示）を `ApplicationModules.verify()` で機械的に強制。
3. **ドキュメント生成** — モジュール構成図（C4 / PlantUML）・module canvas の自動生成。

評価時点の本プロジェクトの実態（#355 調査で確認）:

- **構造**: layer-first（controller / application / domain / infrastructure の各層直下にコンテキストを配置）。Modulith は「メインパッケージ直下のサブパッケージ＝1 モジュール」という module-first を既定とする。**ただし layer-first への固執は無く、module-first への組み替えは選択肢として開いている**ため、構造のすれ違いは決め手にしない。
- **イベント基盤**: 未実装（`ConfirmRaceResultEvent` は `Nothing` 型の TODO のみ。`@DomainEvent` / `ApplicationEventPublisher` 未使用）。
- **永続化**: InMemory リポジトリのみ（`ConcurrentHashMap`）。DB 永続化は未着手（技術選定 #338 が未決）。
- **コンテキスト間連携**: 存在しない。各コンテキストは ArchUnit（`BoundedContextAssignment` の `notDependOnEachOther`）で相互参照を禁止された独立した島であり、イベントで橋渡しすべき連携が 1 本も無い。
- **境界検証**: ArchUnit ＋ jMolecules で既に稼働（オニオン 4 リング・コンテキスト分離・DDD 整合・domain のフレームワーク非依存）。

### 検討した代替案

- **全面採用（layer-first から module-first へ移行し、構造強制も Modulith へ寄せる）**: 技術的には可能で、layer-first への固執も無い。ただし現時点では、既に機能している ArchUnit のコンテキスト分離と役割が重複し、移行コスト（パッケージ再編・ルール書き換え・二重のアーキテクチャテスト併走）が先行する。named interface による細粒度カプセル化は新規の表現力だが、永続化とコンテキスト間連携が無い現状では得る実利が薄い。**永続化とコンテキスト間連携が揃えば、本プロジェクトが探索目的の sandbox である性質（module-first 移行と Modulith 採用そのものが学習・探索の価値を持つ）も加味して、最有力の再評価対象とする**（下記 Decision の再評価スコープ）。
- **部分採用（イベント基盤のみ）**: Event Publication Registry は **JPA / JDBC / MongoDB の永続化が前提**で、`@Async` ＋ `@Transactional(REQUIRES_NEW)` の重い仕組み。これは #330 が**明示的にスコープ外**とした「Spring 連携・永続化・publish-after-commit」そのものであり、#330 が選んだ「イミュータブル集約に同梱する純ドメインイベント（案 A・`Result` 同梱）」と方向が逆。今導入すると永続化と Spring 連携を前倒しで強制してしまう。加えて**発火させる相手（コンテキスト間連携）が現状ゼロ**で、目玉機能に噛ませる対象がいない。

## Decision（決定）

**Spring Modulith は現時点では採用しない。**

- 構造強制は引き続き **ArchUnit ＋ jMolecules** で行う。
- ドメインイベント（#330）は Modulith に依らず、**イミュータブル集約に同梱する軽量な純ドメインイベント（ADR-0009 と整合する案 A）**で独立に進める。
- 以下の**再評価トリガ**を満たした時点で、**全面採用（layer-first から module-first への移行を含む全面導入：構造強制を Modulith の `ApplicationModules.verify()` ＋ named interface へ寄せ、イベント基盤も併せて導入する）**を改めて評価する:
  1. **永続化層が導入されている**（#338 の決着）。Event Publication Registry が前提とする DB が存在すること。
  2. **コンテキスト間の（非同期）連携が実需要として出現している**。イベントで橋渡しすべき具体的なモジュール間フローがあること。

決め手は layer-first との構造的すれ違いではなく（layer-first に固執はしない）、**「目玉機能（信頼性イベント）に噛ませる相手と、それを支える永続化が、今は存在しない」というタイミング**である。再評価時は、本プロジェクトが探索目的の sandbox である性質上、module-first への移行と Modulith 採用そのものを「学習・探索の価値」も含めて全面的に検討する。

## Consequences（結果・影響）

- **得るもの**: 既存の ArchUnit ＋ jMolecules 構成を維持でき、移行コストと二重のアーキテクチャテスト併走を負わない。#330 は永続化・Spring 連携を待たずに軽量パターンで前進できる。
- **引き受けるもの**: Modulith が提供する信頼性イベント・named interface カプセル化・構成図自動生成は当面得られない。将来コンテキスト間連携が増えたとき、イベント配送の信頼性（コミット後発火・失敗時再送）は自前で設計するか、その時点で Modulith 全面採用を再評価する必要がある。
- **再評価の起点**: 上記トリガ（#338 ＋ 実コンテキスト間連携）を満たしたら本 ADR を起点に**全面採用（module-first 移行を含む）**を再評価し、採用するなら新しい ADR を起こして本 ADR を Superseded にする。
- 関連: #355（本評価）/ #330（軽量ドメインイベント）/ #338（永続化層の技術選定）/ [0002](0002-virtual-thread-over-reactive.md)（layer-first・非リアクティブ）/ [0009](0009-immutable-aggregates.md)（イミュータブル集約 = イベント収集方式の制約の出所）。
