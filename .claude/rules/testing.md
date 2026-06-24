# テスト戦略

オニオンアーキテクチャの 4 リングに、テストピラミッドをそのまま写像する。**内側ほど数多く・速く・隔離して、外側ほど少数・統合寄りに**テストする。リングごとに手法が決まっているので、新しいコードはそのリングの流儀に従う。

## リング × テスト手法

| リング | パッケージ | テスト手法 | 参考実装 |
|-------|-----------|-----------|---------|
| domainModel | `domain.*.model` / `domain.shared` | 純粋ユニット（DI なし・Power Assert）。VO の不変条件は `create()` の `Result` を検証 | `MicrochipNumberTest` / `JockeyTest` / `EntityTest` |
| domainService | `domain.*.service` | 純粋ユニット。集約はテスト用 Fixture で組む（モック不要） | `RegisterInStudBookTest` |
| applicationService | `application` | ユニット。Repository ポートを mockk でスタブし、ユースケースの分岐と失敗バリアントを検証 | `RegisterInStudBookUseCaseTest` / `JockeyRegistrationUseCaseTest` |
| adapter (rest) | `controller` | `@WebMvcTest` + `MockMvcTester` の slice テスト。HTTP 入出力と ProblemDetail 描画を検証 | `BloodHorseControllerTest` / `GlobalExceptionHandlerTest` |
| 横断 | — | ArchUnit（規約）／ OpenAPI 契約／ `@SpringBootTest` 統合（最小限） | `ArchitectureTest` / `OpenApiTest` / `HealthEndpointTest` |

方針:

- **モックは applicationService の Repository ポート境界に限る**。ドメイン層は Fixture で実物を組み、モックしない（純粋関数なので隔離コスト不要）。**例外**: 集合制約（一意性等）の検証のためリポジトリポートを引数で取るドメインサービス（[ADR-0022](../../docs/adr/0022-domain-service-repository-for-set-invariants.md)。例: `recordCovering`）のテストでは、そのポートをモックしてよい。
- テスト用 Object Mother（`〜Fixture`）は対象コンテキストの `model` パッケージ配下に **`src/test`** のテストコードとして置く（例: `BloodHorseFixture`）。`java-test-fixtures`（`src/testFixtures` ソースセット）は採らない（本体は単一モジュールで共有需要が無い。issue #326）。前提条件検証を経ずに任意の馬を用意したいときは、前提条件を持たない `public` ファクトリ（例: `BloodHorse.createImported`）で組み立てる（自己検証する `create` を避ける）。生成口は `public` 自己検証ファクトリに統一されており封じ込めは無い（[ADR-0014](../../docs/adr/0014-self-validating-factory-over-confinement.md)）。
- 統合テスト（`@SpringBootTest`）は配線確認の最小限に留める。ロジックの網羅は内側のリングで済ませる（ピラミッドの底を厚く）。
- テスト規約（JUnit 5 / Power Assert / `@WebMvcTest` / 日本語ケース名）の詳細は CLAUDE.md「テスト規約」を参照。

## テスト実行性能（コンテキストキャッシュ優先・並列化しない）

Spring テストの主コストは `ApplicationContext` の構築。速度の本筋は**並列化ではなくコンテキストキャッシュの再利用最大化**にある（実測の根拠は [ADR-0015](../../docs/adr/0015-gradle-build-performance-tuning.md)）。

- **distinct なコンテキスト構成を増やさない**。キャッシュは「同一の unique 構成」のときだけ再利用される（キーは classes / context customizers / active profiles / property sources 等の組合せ）。`@MockkBean` は context customizer を足してキーを分けるので**乱発しない**、`@Import` 構成は揃える、`@SpringBootTest(webEnvironment=...)` を不必要に散らさない。
- **`@DirtiesContext` は原則使わない**（キャッシュを退避させ再構築を強いる）。状態リークは設計で断つ。
- **テスト並列化（`maxParallelForks` / JUnit 5 の `junit.jupiter.execution.parallel`）は採らない**。フォークはキャッシュが JVM 単位のため逆効果、JVM 内並列は `@MockBean`/`@MockkBean` や共有状態を使うテストを Spring 公式が非推奨とする。再評価は #338（永続化層）でテスト隔離を整えてから。
- 速度を縮めたいときの効く順: ビルドキャッシュ/デーモン（[ADR-0015](../../docs/adr/0015-gradle-build-performance-tuning.md)）→ コンテキスト構成の共通化 → （将来）隔離を整えた上での並列化。

## カバレッジハーネス（Kover）

カバレッジは [Kover](https://github.com/Kotlin/kotlinx-kover) で計測する（JaCoCo ではない。理由は [ADR-0006](../../docs/adr/0006-kover-over-jacoco.md)）。設定は `build.gradle.kts` の `kover {}` ブロック。

### 2 つのレポート variant

| variant | 目的 | 対象 | タスク |
|---------|------|------|-------|
| `total` | **穴の可視化**。探索領域も含めた全体像を見せる | 全 main コード（エントリーポイント除く） | `koverHtmlReport` / `koverXmlReport` / `koverLog` |
| `mature` | **リグレッション防止ゲート**。成熟領域だけを検証 | 下記「ゲート対象」のみ | `koverVerifyMature` / `koverLogMature` |

Kover 0.9 の検証ルールはパッケージ単位のフィルタを持てないため、`copyVariant` で `total` を複製した `mature` variant に includes フィルタを掛けてゲート対象を絞っている。

### ゲートの考え方（ラチェット）

- **成熟パッケージのみゲート**: レイヤーごとのテストが揃った領域だけに行カバレッジ下限を課す。探索段階のモデル（`tennis` / `sakamichi` / `breeding` / `race` / `racehorse` / `stallion` 等）は `total` レポートには出すが、ゲートからは外して CI をノイズで赤くしない。
- **現状の下限は行 85%**（実測 88.3% を少し下げてロック）。これは**ラチェット**であり、カバレッジが上がったら下限も引き上げて後戻りを防ぐ。下げるのは設計判断を伴うときだけ。
- ゲート対象に新コードを足すなら、テストも添えて 85% を割らないこと。割ると `./gradlew check`（および CI の `koverVerifyMature`）が失敗する。

ゲート対象パッケージ（`build.gradle.kts` の `variant("mature")` の includes が唯一の出所。ここは要約）:
`domain.shared` / `domain.racing.model.jockey` / `domain.studbook.model.horse.bloodhorse` / `domain.studbook.service.horse` / `application.studbook` / `application.racing.jockey` / `controller`。

### 実行

```bash
./gradlew koverHtmlReport      # build/reports/kover/html/index.html で穴を目視（total）
./gradlew koverVerifyMature    # 成熟ゲートの検証（check にも組み込み済み）
./gradlew check                # ktfmt + detekt + test + koverVerifyMature を一括実行
```

CI（`api-tests.yml`）は test 後に `koverVerifyMature` でゲートを掛け、`koverLog` / `koverLogMature` の数値を PR の Job Summary に出す（外部サービス不使用）。

## 当面の宿題（カバレッジの穴）

`total` レポートで 0% に見える領域は、成熟させるときにテストを添える。優先度は実装の成熟度に従う:

- `infrastructure.*`（InMemory リポジトリ）: ポート実装の契約テストが未整備
- `domain.racing.service`（`confirmRaceResult`）: サービスだがテスト無し
- `domain.racing.model`（`race`）・`sakamichi` / `tennis`: 探索段階のモデル

これらは成熟してゲート対象に昇格する時点で `variant("mature")` の includes に追加する。
