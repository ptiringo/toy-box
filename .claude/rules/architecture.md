# アーキテクチャ規約

本プロジェクトはオニオンアーキテクチャ（ドメインモデル / ドメインサービス / アプリケーションサービス / アダプターの 4 リング）を採用する。規約は ArchUnit（`src/test/kotlin/com/example/api/architecture/` 配下の `〜RulesTest` 群）で機械的に強制されており、違反すると `./gradlew test` が失敗する。規約は関心ごとに分割してあり、`OnionLayerRulesTest`（レイヤー依存方向・stereotype 配置）/ `DomainModelingRulesTest`（jMolecules・イミュータブル集約）/ `ControllerContractRulesTest`（HTTP 契約・パッケージ構成）/ `BoundedContextRulesTest`（コンテキスト分離）/ `GeneralCodingRulesTest`（一般コーディング規約）に置く。共有の述語・定数・スライス割り当ては `ArchSupport.kt` に集約する。**新しいコードを書くときは以下の規約に従うこと。規約を変えたい場合はテストと本ファイルを同時に更新する。**

## レイヤー依存ルール（オニオン 4 リング）

内側ほど安定し、依存の矢印は常に外→内に向く。`onionArchitecture()` で強制している。

```
                  ┌─────────────── adapter ───────────────┐
                  │  controller / infrastructure / mcp    │
                  │   ┌────── applicationService ──────┐  │
                  │   │   ┌─── domainService ───┐       │  │
                  │   │   │  ┌ domainModel ┐    │       │  │
                  │   │   │  │ shared+model │   │       │  │
                  │   │   │  └──────────────┘   │       │  │
                  │   │   └─────────────────────┘       │  │
                  │   └─────────────────────────────────┘  │
                  └────────────────────────────────────────┘
```

| リング | パッケージ | 依存してよい先 | Spring 依存 |
|-------|-----------|--------------|------------|
| domainModel | `domain.shared` + `domain.*.model` | 純粋ライブラリのみ（kotlin-result / java-uuid-generator / jMolecules） | 禁止（jakarta / Jackson も禁止） |
| domainService | `domain.*.service` | domainModel | 禁止 |
| applicationService | `application` | domainModel / domainService | `org.springframework.stereotype`（`@Service` / `@Component`）のみ |
| adapter (rest) | `controller` | 内側すべて | 可 |
| adapter (persistence) | `infrastructure` | 内側すべて | 可 |
| adapter (mcp) | `mcp` | 内側すべて | 可 |

- **ドメインサービスはドメインモデルにのみ依存でき、その逆（モデル→サービス）は禁止**
- アダプター同士（`controller` ⇔ `infrastructure` ⇔ `mcp`）の参照は禁止
- `@RestController` は `controller`、`@Service` は `application`、Spring の `@Repository`（ポート実装）は `infrastructure` に置く
- `@McpTool` を持つ Spring Bean は `mcp` に置く（application 層に直付けしない）。ArchUnit は `ArchSupport.kt` の `adapter("mcp", MCP)` で `mcp` を adapter リングとして強制する（[ADR-0035](../../docs/adr/0035-mcp-interface-adapter.md)）

### ドメインモデルとドメインサービスの分け方

各コンテキストの配下を `model/` と `service/` に分割する（パッケージ構造を参照）。

- **model**: Entity / Value Object / Repository ポート（interface）/ 集約のロジック。**生成は集約の `public` 自己検証ファクトリ（`create` 等）に集約**し、不変条件を検証して `Result` を返す。集約をまたぐ前提条件でも、**協力集約を引数で受け取って構築時に検証できるものはファクトリの責務**とする（例: `BloodHorse.create(sire, dam, …)` が父=雄・母=雌・DNA・品種整合を自己検証）。生成口の封じ込め（`internal` 化等）は行わない（[ADR-0014](../../docs/adr/0014-self-validating-factory-over-confinement.md)）。集約が**フィールドで**他集約を参照する場合は ID のみ（jMolecules ルール）
- **service**: **単一集約の構築ではない、複数集約をまたぐオーケストレーション／手続き**のドメインロジック（例: `registerFoal` は分娩結果の判定→`BloodHorse.create` へ橋渡し、`confirmRaceResult`）。**Kotlin のトップレベル関数で書く**（`object` でラップしない）。jMolecules の `@Service` は付けない（パッケージ配置で表現し、`service/` に居ることがドメインサービスの証）。役割分担は「構築時バリデーション＝ファクトリ／入力参照の解決・操作対象のロード・保存・オーケストレーション＝アプリケーション層」を基本とする。**例外: 集合制約（一意性・件数上限など、既存レコード集合への問い合わせが必要なドメイン不変条件）の検証に限り、ドメインサービスがリポジトリ「ポート」を引数で受け取って読み取り引き当てしてよい**（書き込みはしない・ポートは `domain.*.model` の interface に限る。例: `recordCovering` が `BreedingResultRepository` を受け取り種付年の一意性を検証）。経緯は [ADR-0022](../../docs/adr/0022-domain-service-repository-for-set-invariants.md)
- 1 ファイルにモデルとサービスを混在させない（例: `confirmRaceResult` は `service/race/` に、入力 VO の `RaceResult` は `model/race/` に置く）

### 読み取り経路（軽量 CQRS / L2）

読み取り（クエリ）系は、書き込み側の集約を**経由しない**独立経路として実装する（軽量 CQRS = L2。ストア分離・結果整合・イベントソーシング = L3 は採らない）。決定経緯は [ADR-0031](../../docs/adr/0031-lightweight-cqrs-read-model.md)、リファレンス実装は `racing/jockey`（`FindJockeyUseCase`）。

- **Read Model（View）とクエリポートは `application` に置く**（ドメインを汚さない）。書き込みの「集約 + Repository ポートは `domain.*.model`」と非対称だが、これは意図したもの。読みモデルは集約のライフサイクル（生成・状態遷移・整合性境界）を持たないため domain には置かない
- **View には jMolecules の `@QueryModel`（`org.jmolecules.architecture.cqrs`）を付ける**。不変条件を持たないフラットな DTO で、値の等価性が自然なため `data class` でよい。`@QueryModel` が `application` に居ることは ArchUnit [`queryModelsResideInApplication`] で強制する（DDD ビルディングブロックを `domain.*.model` に縛る [`dddBuildingBlocksResideInDomainModel`] と対称）
- **クエリポート（`〜Queries`）は plain interface とし、書き込みポートの jMolecules `@Repository` は付けない**。読み取りは Repository ビルディングブロックではないため
- **infrastructure の実装は集約を組まずストアから直接 View へ詰める**（例: `JdbcJockeyQueries` が `JdbcClient` で `jockey` を直 SELECT し、書き込みの `JockeyRow`／集約を経由しない）。同じテーブルを読んでも、経路（write=集約復元 / read=View 直組み）とモデルを分離するのが L2 の価値であり、「write ポートに finder を生やす」誘惑には乗らない
- **読み取りも write と対称に `application` を必ず通す**（現行オニオンの依存方向を維持。infrastructure → application のポート実装はアダプターの依存として onion ルールが許容する）
- クエリ入力 DTO は `〜Query` サフィックス。書き込み系の `Command<T>` 封筒（発生時刻メタデータ）は読み取りでは**使わない**（発生時刻は書き込みイベントの概念）

## パッケージ構造

```
domain/
├── shared/                      # 共有カーネル（Command / Entity 基底）。全コンテキストから参照可
├── studbook/                    # 軽種馬登録コンテキスト（JAIRS: 血統登録・繁殖登録）
│   ├── model/                   # ドメインモデルリング
│   │   ├── breeding/
│   │   └── horse/...
│   └── service/                 # ドメインサービスリング
│       ├── breeding/            #   recordCovering, recordUncovered
│       └── horse/               #   registerFoal
├── racing/                      # 競馬コンテキスト（JRA: 騎手・競走）
│   ├── model/                   # ドメインモデルリング
│   │   ├── jockey/              #   Jockey, JockeyId, JockeyRepository
│   │   └── race/                #   Race, RaceResult, ...
│   └── service/                 # ドメインサービスリング
│       └── race/                #   confirmRaceResult
├── sakamichi/model/
└── tennis/model/
```

## 境界づけられたコンテキストの分離

`application` / `domain` / `infrastructure` 各層の直下のパッケージ名（`studbook` / `racing` / `sakamichi` / `tennis`）を境界づけられたコンテキストとみなし、**コンテキスト間の依存は層やリングをまたぐ場合も含めて一切禁止**する（例: `application.studbook` → `domain.tennis.model` は違反）。`model` / `service` のサブ階層はコンテキスト名の判定に影響しない。

- `domain.shared` は共有カーネルであり、コンテキスト分離の対象外（どのコンテキストからも参照可）
- 新しいコンテキストを追加する場合、`<context>/model/`（必要なら `service/`）を切るだけで自動的に分離ルールの対象になる

## DDD ビルディングブロック（jMolecules）

ドメインモデルには [jMolecules](https://github.com/xmolecules/jmolecules) のアノテーション（`org.jmolecules.ddd.annotation.*` / `org.jmolecules.event.annotation.*`）で役割を表明する。アノテーションはメタデータのみでランタイム挙動を持たないため domain 層に置いてよい。

| 対象 | アノテーション | 例 |
|------|--------------|-----|
| 集約ルート | `@AggregateRoot` | `Jockey`, `Race` |
| 集約内エンティティ | `@Entity` | （現状なし） |
| 値オブジェクト（ID 値クラス含む） | `@ValueObject` | `JockeyId` |
| 識別子プロパティ | `@field:Identity` | `Jockey.id` |
| Repository ポート（interface） | `@Repository`（jMolecules 版） | `JockeyRepository` |
| ドメインイベント | `@DomainEvent`（jMolecules events 版） | `HorseNamed` |

ドメインサービス（`service/` のトップレベル関数）には jMolecules アノテーションを付けない。`@Service`（jMolecules）は型向けでトップレベル関数に付けられず、ドメインサービスであることは `service/` パッケージへの配置で表現する。

**ドメインイベント**（`@DomainEvent`）は「起きたこと」を表すビルディングブロック。イミュータブル集約（`var` 禁止）は内部にイベントを溜め込めないため、状態遷移メソッドが遷移後の集約とイベントを `StateTransition<A, E>`（`domain.shared`）に同梱して返し、発行は application 層が担う（集約は純粋なまま保つ）。失敗しうる遷移は `Result<StateTransition<A, E>, エラー>` を返し失敗時はイベントを生成しない（例: `BloodHorse.assignName` → `HorseNamed`）。イベントは値としての等価性が自然なため `data class` を使ってよい（ID ベース `final equals` を持つ集約と異なり衝突しない）。他集約への参照は ID 値クラス経由。収集・発行方式の決定経緯は [ADR-0029](../../docs/adr/0029-domain-events-via-state-transition-return.md)（Spring `ApplicationEventPublisher` 連携・publish-after-commit・発生時刻 enrichment は別イシュー送り）。

注意点:

- `@Identity` は FIELD / METHOD ターゲットのため、Kotlin プロパティには **`@field:Identity`** と use-site target を明示する
- jMolecules アノテーション付きクラス（`@DomainEvent` を含む）はドメインモデルリング（`domain.*.model`）にのみ置ける（ArchUnit `dddBuildingBlocksResideInDomainModel` で強制）。`StateTransition` 等の汎用キャリアはアノテーションを持たないため `domain.shared` に置いてよい
- `JMoleculesDddRules.all()` により以下が強制される:
  - `@Entity` / `@AggregateRoot` は `@Identity` 付き識別子を持つ
  - **他の集約への参照は ID 値クラス（または `Association`）経由のみ**。集約オブジェクトを直接フィールドに持ってはならない（例: `Stallion` は `BloodHorse` ではなく `BloodHorseId` を持つ）
  - `@ValueObject` は Entity / AggregateRoot を参照しない
- 軽量 CQRS の読みモデル `@QueryModel`（`org.jmolecules.architecture.cqrs`）は DDD ビルディングブロックではなく **CQRS アーキテクチャ注釈**。`domain.*.model` ではなく `application` に置き、ArchUnit `queryModelsResideInApplication` で強制する（読み取り経路の全体方針は「レイヤー依存ルール」の「読み取り経路（軽量 CQRS / L2）」、決定経緯は [ADR-0031](../../docs/adr/0031-lightweight-cqrs-read-model.md)）

## その他の強制ルール

- 標準出力・標準エラーへの直接書き込み禁止（ロガーを使う）
- フィールドインジェクション禁止（コンストラクタインジェクションを使う）
- `UUID.randomUUID()` の直接呼び出し禁止。ID は `domain.shared.generateId()`（UUIDv7 相当のタイムベース生成）経由で生成する（永続化時のインデックス局所性のため。[ADR-0005](../../docs/adr/0005-time-based-uuid-generation.md)）。main コードのみ対象（テストの fixture は対象外）
- **集約（`@AggregateRoot` / `@Entity`）はイミュータブル**（`val` のみ・`var` 禁止）。状態遷移は対象を書き換えず、同一性（ID）を引き継いだ新インスタンスを返すメソッドで表す（[ADR-0009](../../docs/adr/0009-immutable-aggregates.md)）。`val` は final フィールド・`var` は非 final フィールドへコンパイルされるため、集約クラスが直接宣言するフィールドが全て final であることを ArchUnit で検証して `var` を排除する
- **集約（`@AggregateRoot` / `@Entity`）は `data class` を使わない**（[ADR-0009](../../docs/adr/0009-immutable-aggregates.md)）。`data class` は全プロパティから `equals` / `hashCode` を生成し、ID ベースの `final equals` / `hashCode` と衝突するため、`private constructor` ＋手書き `copy` で写像する。`data class` は各プロパティに `componentN()` を生成するため、集約クラスが `componentN()` を持たないことを ArchUnit で検証して `data class` を排除する（手書き `copy` は `componentN()` を生成しないため誤検出されない。ルールが実際に違反を検出することは `AggregateNotDataClassRuleTest` で別途担保）
- **ドメインサービス（`domain.*.service`）はトップレベル関数で書く**（`object` / `class` でラップしない）。トップレベル関数はファイルごとのファサードクラス（`〜Kt`）へコンパイルされるため、service パッケージ内のクラスが `Kt` で終わることを ArchUnit で検証して `object` / `class` 宣言を排除する。ただしサービスの戻り値（`Result<_, 〜Error>`）の失敗側を表す失敗バリアント型（`〜Error`）はサービスと同居させてよく、対象から除外する
- **`@RestController` のハンドラは成功レスポンスで `ResponseEntity` を返さない**。成功は `@ResponseStatus` ＋戻り値で resource を返す（[error-handling.md](error-handling.md) / [api-design.md](api-design.md)）。エラー描画 funnel の `GlobalExceptionHandler` は `@RestController` ではない（`ResponseEntityExceptionHandler` 継承）ため誤検出されない
- **HTTP 契約（request / response DTO）はフィールド型にドメイン enum を持たない**。`controller` 層に契約専用の `〜Dto` enum を置き、`toDomain()` / `toApi()` でマッピングする（[api-design.md](api-design.md) / [ADR-0007](../../docs/adr/0007-wire-enum-dto-decoupling.md)）。`controller` 配下のフィールドがドメイン（`domain..`）の enum を raw type に持たないことを ArchUnit で検証する。マッパー関数はドメイン enum を引数・戻り値で扱うが、フィールド型のみを対象とするため誤検出されない（ルールが実際に違反を検出することは `DtoDomainEnumRuleTest` で別途担保）
- **読み取りモデル（`@QueryModel`）は `application` 層に置く**。軽量 CQRS（L2）の読み取り側として、書き込み集約を経由しないフラットな Read Model を application に置く（[ADR-0031](../../docs/adr/0031-lightweight-cqrs-read-model.md)）。`@QueryModel` 付きクラスが `application` 配下に居ることを ArchUnit `queryModelsResideInApplication` で検証する（DDD ビルディングブロックを `domain.*.model` に縛る `dddBuildingBlocksResideInDomainModel` と対称）。詳細は「読み取り経路（軽量 CQRS / L2）」
- **ドメイン / アプリケーション層では `throw` しない**。業務エラーは `Result<V, E>` で返し、プログラミングエラーは `require` / `check` を使う（[error-handling.md](error-handling.md)）。`domain..` / `application..` 配下の明示的な `throw` 式を **detekt カスタムルール** `NoThrowInDomainAndApplication`（`:detekt-rules` モジュール）で検出してビルドを失敗させる。例外送出は `infrastructure`（インフラ障害）と Controller 境界の `orThrowProblem()` に限るため、両者はパッケージ判定で対象外。`require` / `check` / `error` は `throw` 式ではないため検出されない。テストコードは detekt 設定の `excludes` で対象外
- **コンパイラ警告をエラー化する（警告ゼロ運用）**。両モジュール（root `api` / `:detekt-rules`）の Kotlin `compilerOptions` に `allWarningsAsErrors = true` を設定し、コンパイラ警告が 1 件でもあればビルドを失敗させる（[ADR-0019](../../docs/adr/0019-compiler-warnings-as-errors.md)）。CI は ktfmt の後の専用コンパイルステップで明示ゲートする（detekt / test 内のコンパイルでも効くが原因可視化のため）。個別に許容する警告は `@Suppress` または `-Xwarning-level=<ID>:warning` で逃がす。注意: K2 では未使用ローカル変数は警告化されないため、ルールの空振り検証（ミューテーション）は `@Deprecated` 呼び出し等で行う

> 強制手段は ArchUnit（`src/test/.../architecture/`）に限らない。`throw` 文のような構文レベルの規約は ArchUnit では検出しづらいため、detekt のカスタムルールを `:detekt-rules` モジュール（`RuleSetProvider` を ServiceLoader 登録）に置き、本体 build の `detektPlugins` に組み込んで `./gradlew detekt` で強制する。ルール挙動の検証テストは同モジュール内（`detekt-test` 使用）に置く。

### 機械強制しない規約（レビューで担保）

- **REST リソース操作の成功レスポンスは一律でリソース表現を返す**（[ADR-0008](../../docs/adr/0008-uniform-resource-representation-response.md) / [api-design.md](api-design.md)）は**機械強制しない**。この規約の本質は「同一リソースの全操作が同一の単一表現 DTO（`〜Response`）を返す」という**リソース単位の意味的一貫性**であり、ハンドラを「どのリソースを操作するか」で束ねる必要がある。ArchUnit / detekt はパッケージ・型・アノテーション・構文は検査できるが、この「リソース帰属によるグルーピング」を表現できない。名前ベースの近似（`Register〜Response` の禁止等）は、AIP-136 がリソース外の付加情報返却に専用 `Response` を認める余地（ADR-0008 でも追補余地として明記）と衝突して誤検出を生むうえ、現に移行途上の `RegisterJockeyResponse`（ADR-0008 が単一 DTO へ寄せる対象とする既知の不整合）を機械的に「違反」と断ずるだけで規約の意味は捉えられない。したがって api-design.md のルール文＋レビューで担保する（#307 の調査結論）。

## ルールの変更・追加

- アーキテクチャ違反でテストが落ちた場合、**原則コードをアーキテクチャに合わせる**。ルール側を変えるのは設計判断の変更時のみ
- 依存ライブラリ: `jmolecules-bom`（バージョン管理）+ `jmolecules-ddd`（main）、`archunit-junit5` + `jmolecules-archunit`（test）。いずれも version catalog（`gradle/libs.versions.toml`）で管理
