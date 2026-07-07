---
paths:
  - "src/test/**"
  - "build.gradle.kts"
---

# テスト戦略

オニオンアーキテクチャの 4 リングに、テストピラミッドをそのまま写像する。**内側ほど数多く・速く・隔離して、外側ほど少数・統合寄りに**テストする。リングごとに手法が決まっているので、新しいコードはそのリングの流儀に従う。

## リング × テスト手法

| リング | パッケージ | テスト手法 | 参考実装 |
|-------|-----------|-----------|---------|
| domainModel | `domain.*.model` / `domain.shared` | 純粋ユニット（DI なし・Power Assert）。VO の不変条件は `create()` の `Result` を検証 | `MicrochipNumberTest` / `JockeyTest` / `EntityTest` |
| domainService | `domain.*.service` | 純粋ユニット。集約はテスト用 Fixture で組む（モック不要） | `RegisterInStudBookTest` |
| applicationService | `application` | ユニット。Repository ポートを mockk でスタブし、ユースケースの分岐と失敗バリアントを検証 | `RegisterInStudBookUseCaseTest` / `JockeyRegistrationUseCaseTest` |
| adapter (rest) | `controller` | `@WebMvcTest` + `MockMvcTester` の slice テスト。HTTP 入出力と ProblemDetail 描画を検証 | `BloodHorseControllerTest` / `GlobalExceptionHandlerTest` |
| 横断 | — | ArchUnit（規約）／ OpenAPI 契約／ `@SpringBootTest` 統合（最小限）／ **E2E（RestTestClient・実配線・ゲート外）** | `architecture/` の `〜RulesTest` 群 / `OpenApiTest` / `HealthEndpointTest` / `JockeyApiE2eTest` |

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

## E2E（ブラックボックス API テスト・ゲート外）

全層を実配線したまま HTTP 越しに叩く E2E は素の Spring ネイティブ（`RestTestClient`）で書く（`src/e2eTest`、決定は [ADR-0056](../../docs/adr/0056-drop-karate-native-resttestclient-e2e.md)。Karate は [ADR-0039](../../docs/adr/0039-e2e-api-tests-with-karate.md) で採用したが用途に対し過大なため撤退）。`@SpringBootTest(RANDOM_PORT)` + `@AutoConfigureRestTestClient` + Testcontainers PostgreSQL でアプリを起動し、`RestTestClient` でシナリオを流す（`HealthEndpointTest` と同型）。遅く探索的なため **ArchUnit / Kover / `check` / pre-push のいずれの対象にもしない**（独立ソースセット `e2eTest` + タスク `e2eTest`）。CI は独立ワークフロー `e2e-tests.yml` で回す。ローカルは必要時に `./gradlew e2eTest`。網羅はここで広げず内側リングで担保する（ピラミッドの底を厚く）。

## カバレッジハーネス（Kover）

カバレッジは [Kover](https://github.com/Kotlin/kotlinx-kover) で計測する（JaCoCo ではない。理由は [ADR-0006](../../docs/adr/0006-kover-over-jacoco.md)）。設定は `build.gradle.kts` の `kover {}` ブロック。

### 2 つのレポート variant

| variant | 目的 | 対象 | タスク |
|---------|------|------|-------|
| `total` | **穴の可視化**。探索領域も含めた全体像を見せる | 全 main コード（エントリーポイント除く） | `koverHtmlReport` / `koverXmlReport` / `koverLog` |
| `mature` | **リグレッション防止ゲート**。成熟領域だけを検証 | 下記「探索除外パッケージ」以外の全 main コード | `koverVerifyMature` / `koverLogMature` |

Kover 0.9 の検証ルールはパッケージ単位のフィルタを持てないため、`copyVariant` で `total` を複製した `mature` variant に excludes フィルタを掛けてゲート対象を絞っている（探索段階のパッケージだけ除外し、残りを全てゲートする）。

### ゲートの考え方（ラチェット）

- **excludes 反転でゲート対象を決める**: 全体をゲート対象とし、探索段階のパッケージだけを `variant("mature")` の `excludes` に列挙する。パッケージが成熟したら `excludes` から外す＝ゲート対象へ昇格。**外し忘れても「より厳しくなる」安全側**（includes 方式では追加忘れが「緩くなる」危険側だった）。
- **カバレッジ単位は LINE と BRANCH の 2 ボーンド**。下限は反転後母集団の実測直下のキリ番（LINE 90% / BRANCH 80%）に固定した**手動ラチェット**。実測が上がったら手で下限を上げてよい。割ると `./gradlew check`（CI の `koverVerifyMature`）が失敗する。
- **自動ラチェット機構は持たない**（YAGNI）。手動で引き上げる。

探索除外パッケージ（`build.gradle.kts` の `variant("mature")` の `excludes` が唯一の出所。ここは要約）:
`domain.racing.model.race` / `domain.racing.service` / `domain.tennis` / `e2e`（E2E テスト基盤）/ `dbdoc`（tbls ドキュメント生成基盤）。

集約ゲート（本見直し）は成熟領域全体の絶対水準を守り、patch coverage（[#437](https://github.com/ptiringo/toy-box/issues/437)）は新規・変更コードのカバレッジを別途課す補完関係にある。

### 差分ゲート（patch coverage・diff-cover）

集約ゲート（成熟領域全体の絶対水準）を補完し、PR で変更した行のカバレッジを個別に検証するのが差分ゲートである（[ADR-0055](../../docs/adr/0055-patch-coverage-diff-cover-gate.md)）。母集団が厚い集約ゲートだけでは新規変更行のテスト漏れが薄まって見逃されるため、変更行そのものに閾値を課す。

- **[diff-cover](https://github.com/Bachmann1234/diff-cover)**（pipx, 10.3.0, mise 管理）で変更行カバレッジを算出し、`--fail-under 90` で 90% 未満の PR を落とす。
- 入力は `koverVerifyMature` と同じ `mature` variant の XML（`build/reports/kover/reportMature.xml`）。探索領域（`excludes` denylist、出所は `build.gradle.kts` に一元化）は mature XML の時点で除外済みのため、差分ゲートにも自動で継承される。
- CI（`api-tests.yml`）は **PR ジョブ限定**で実行する（`github.event_name == 'pull_request'`）。main への push では走らせない（push でも走る `koverVerifyMature` が最終防波堤）。

ローカル確認:

```bash
./gradlew koverXmlReportMature
mise exec -- diff-cover build/reports/kover/reportMature.xml \
  --compare-branch origin/main --src-roots src/main/kotlin --fail-under 90
```

`--src-roots src/main/kotlin` は必須（省略するとソースパスを解決できずレポートが空になる）。

### 実行

```bash
./gradlew koverHtmlReport      # build/reports/kover/html/index.html で穴を目視（total）
./gradlew koverVerifyMature    # 成熟ゲートの検証（check にも組み込み済み）
./gradlew check                # ktfmt + detekt + test + koverVerifyMature を一括実行
```

CI（`api-tests.yml`）は test 後に `koverVerifyMature` でゲートを掛け、PR ジョブでは続けて差分ゲート（diff-cover）も検証する。`koverLog` / `koverLogMature` の数値と差分ゲートの Markdown レポートは、いずれも PR の Job Summary に出す（外部サービス不使用）。

## 当面の宿題（カバレッジの穴）

`total` レポートで 0% に見える領域は、成熟させるときにテストを添える。優先度は実装の成熟度に従う:

- `infrastructure.*`（JDBC リポジトリ）: `Jockey` は Testcontainers 契約テスト済み。残り集約（`BloodHorse` / `BreedingRegistration` / `BreedingResult`）は JDBC 実装＋契約テストが未整備で、移行に伴い InMemory を廃止する（JDBC 一本化。[ADR-0030](../../docs/adr/0030-jdbc-only-persistence-retire-inmemory.md) / #435）。なお `infrastructure.*` は `excludes` に入れておらず**既にゲート母集団内**。未テスト分は集計に乗るが現状の LINE 90% / BRANCH 80% を満たしている（割り込んだらテストを添えること）。
- `domain.racing.service`（`confirmRaceResult`）: サービスだがテスト無し。`excludes` 在籍。
- `domain.racing.model`（`race`）・`tennis`: 探索段階のモデル。`excludes` 在籍（`sakamichi` は #367 でテストが揃いゲート対象へ昇格済み）。

このうち `excludes` に在籍している探索領域（`racing.model.race` / `racing.service` / `tennis`）は、テストが揃った時点で `variant("mature")` の `excludes` から外してゲート対象へ昇格させる（`infrastructure.*` は既にゲート対象なので対象外）。
