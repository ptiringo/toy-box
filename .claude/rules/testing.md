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

## 記法

- **JUnit 5（`org.junit.jupiter.api.Test`）を使う**。`kotlin.test.Test` は使わない（マルチプラットフォーム対応が不要なため）。
- **アサーションは Kotlin の `assert` 関数**（Power Assert が式を分解して表示する）。
- **テストケース名は日本語**でテストの意図を表す。
- **コントローラーの slice テストは `@WebMvcTest` + `MockMvcTester`**（実例: `HelloControllerTest` / `BloodHorseControllerTest`）。認証フィルタは `@AutoConfigureMockMvc(addFilters = false)` で無効化する（slice は HTTP 契約の検証に集中させる。認証は `SecurityConfigTest` と E2E が担保）。
- **アプリ全体の統合テストは `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` + `RestTestClient`**（Spring Framework 6.2 の `RestClient` ベース sync テストクライアント）。
- **Controller に横断 Bean（例: `Clock`）を注入したら、その Controller を載せる _全_ slice テストに `@Import` を足す**。slice は web 層しかロードしないため、足し忘れると `NoSuchBeanDefinitionException` でコンテキスト生成が落ちる。落ちるのは対象 Controller のテストだけとは限らず、その Controller を踏み台にしている別テスト（`GlobalExceptionHandlerTest` 等）まで芋づるで落ちる。`grep -rn "WebMvcTest(" src/test` で載せている slice を全部洗い出すこと。Bean 定義は `ApiApplication` の `@Bean` ではなく専用 `@Configuration` に切り出すと `@Import` 単独で取り込める。
- **`@WebMvcTest` には `@MockkBean AccountRepository` と `@MockkBean WorldQueries` が要る**。`WebMvcConfig`（`CurrentAccountArgumentResolver` と `ActorArgumentResolver` の登録元）が `WebMvcConfigurer` である以上、両 resolver は全 `@WebMvcTest` スライスに自動で載る。依存 Bean を差し込まないとコンテキスト生成が `NoSuchBeanDefinitionException` で loud に落ちる（コンテキストキャッシュの分岐は増えない。忘れても即座に検知できる）。`Actor` を要求するハンドラ（ドメイン API）を叩くテストは、さらに `@MockkBean ActorArgumentResolver` を置いて `supportsParameter` / `resolveArgument` をスタブし固定 `Actor` を返す（slice は認証フィルタを無効化しており実解決を走らせられないため）。
- **DB を触るテストで世界（`world_id`）が要るときは `PostgresContainerSupport.createWorld()` を使う**（#704 / ADR-0067）。世界スコープ化以降、ドメインの行は必ずいずれかの世界に属する。`createWorld()` は呼ぶたびにアカウントと世界を 1 組作るので、「他人の世界」を組み立てる検証にも使える。**フィールド初期化ではなく `@BeforeEach` で呼ぶこと**（基底クラスの TRUNCATE がフィールド初期化の後に走るため、先に作った世界は消える）。`WorldId` は value class で `lateinit` を付けられないので、生 `UUID` を `lateinit` で保持して `get()` で包む。

## 実行

```bash
./gradlew test                        # 全テスト
./gradlew :test --tests "JockeyTest"  # 単一クラス（先頭のコロンが必須）
./gradlew check                       # ktfmt + detekt + test + koverVerifyMature を一括
```

- **`--tests` は `:test`（先頭コロン＝ root プロジェクトの test）に付ける**。`./gradlew test --tests …` はビルドに含まれる `:detekt-rules` の `test` にも同じフィルタが適用され、`No tests found for given includes` でビルドが失敗する。
- **Kotlin の変更は `test` ではなく `check` で締める**。ArchUnit の規約テスト・detekt カスタムルール・`koverVerifyMature`（成熟ゲート）は focused な `--tests` 実行では走らないため、緑を見て完了と判断すると `check` で落ちる。
- **CI でコンパイルだけしたいときは `assemble` を使う**（例: CodeQL のビルドステップ）。`build -x test` は `check` 配下の検証（ktfmt / detekt / `koverVerifyMature`）を巻き込み、コンパイルが目的のジョブでカバレッジゲートを誤発火させる。

## DB を触るテストの後始末（基底クラスが担う・テスト側に書かない）

Testcontainers の PostgreSQL はプロセス内で共有されるため、テストが書いた行は消さない限り後続へ漏れる。後始末は `PostgresContainerSupport`（`src/test/.../support/`）の `@BeforeEach` に一元化されている（[ADR-0070](../../docs/adr/0070-db-test-cleanup-via-truncate-not-transactional.md)）。

- **テスト側にクリーンアップを書かない**。基底クラスを継承した時点で必ず効く（`src/test` だけでなく `src/e2eTest` / `src/replay` も同じ基底クラスを継承する）。対象テーブルは `pg_tables` から動的に列挙するので、マイグレーションでテーブルが増えても手で追従する必要はない。
- **`@Transactional` によるロールバック分離は採らない**。トランザクション意味論を検証するテスト（publish-after-commit / ユースケース Tx 境界）が実コミットを要求するため適用範囲を 100% にできず、隔離方式が二重化する（実測の根拠は [ADR-0070](../../docs/adr/0070-db-test-cleanup-via-truncate-not-transactional.md)）。
- テスト本文の `rows.deleteAll()` は後始末ではなく「並行削除の再現」という検証の一部。混同して消さないこと。

## ローカルゲートと Docker

pre-push（lefthook の `full-test`）は `./gradlew test` を丸ごと回す。**Testcontainers 依存テストも対象のまま**で、Docker 不要なテストだけを切り出す運用は採らない（[ADR-0071](../../docs/adr/0071-pre-push-docker-fail-fast-guard.md)。穴が空くのが永続化契約テストという最も守りたい場所になること、`@Tag` の付け忘れが危険側に転ぶことが理由）。したがって **push には Docker が要る**。

- pre-push の先頭で `scripts/check-docker-available.sh` が Docker 到達性を確認し、駄目なら理由と対処を出して即座に落とす（`piped: true` で `full-test` は走らない）。ハングして「push が無反応」に見える状態を潰すためのガードで、ゲートの範囲は変えない。
- 判定は `docker info` の成否のみ。**Docker は生きているが Testcontainers だけ失敗する**ケース（イメージの pull 不可・リソース枯渇等）はガードを素通りし、従来どおりテストの失敗として出る。
- Docker が復旧しないまま push したいときは **`LEFTHOOK_EXCLUDE=docker-available,full-test git push`**（`--no-verify` は gitleaks 等まで飛ばすので最後の手段）。同じテストは CI（`api-tests.yml`）で走る。

## テスト実行性能（コンテキストキャッシュ優先・並列化しない）

Spring テストの主コストは `ApplicationContext` の構築。速度の本筋は**並列化ではなくコンテキストキャッシュの再利用最大化**にある（実測の根拠は [ADR-0015](../../docs/adr/0015-gradle-build-performance-tuning.md)）。

- **distinct なコンテキスト構成を増やさない**。キャッシュは「同一の unique 構成」のときだけ再利用される（キーは classes / context customizers / active profiles / property sources 等の組合せ）。`@MockkBean` は context customizer を足してキーを分けるので**乱発しない**、`@Import` 構成は揃える、`@SpringBootTest(webEnvironment=...)` を不必要に散らさない。
- **`@DirtiesContext` は原則使わない**（キャッシュを退避させ再構築を強いる）。状態リークは設計で断つ。
- **テスト並列化（`maxParallelForks` / JUnit 5 の `junit.jupiter.execution.parallel`）は採らない**。フォークはキャッシュが JVM 単位のため逆効果、JVM 内並列は `@MockBean`/`@MockkBean` や共有状態を使うテストを Spring 公式が非推奨とする。加えて DB を触るテストの後始末（共有コンテナの全テーブル TRUNCATE。[ADR-0070](../../docs/adr/0070-db-test-cleanup-via-truncate-not-transactional.md)）は JVM 内並列と両立しない（他スレッドのデータまで消す）。再評価は #690。
- 速度を縮めたいときの効く順: ビルドキャッシュ/デーモン（[ADR-0015](../../docs/adr/0015-gradle-build-performance-tuning.md)）→ コンテキスト構成の共通化 → （将来）隔離を整えた上での並列化。

## E2E（ブラックボックス API テスト・ゲート外）

全層を実配線したまま HTTP 越しに叩く E2E は素の Spring ネイティブ（`RestTestClient`）で書く（`src/e2eTest`、決定は [ADR-0056](../../docs/adr/0056-drop-karate-native-resttestclient-e2e.md)。Karate は [ADR-0039](../../docs/adr/0039-e2e-api-tests-with-karate.md) で採用したが用途に対し過大なため撤退）。`@SpringBootTest(RANDOM_PORT)` + `@AutoConfigureRestTestClient` + Testcontainers PostgreSQL でアプリを起動し、`RestTestClient` でシナリオを流す（`HealthEndpointTest` と同型）。遅く探索的なため **ArchUnit / Kover / `check` / pre-push のいずれの対象にもしない**（独立ソースセット `e2eTest` + タスク `e2eTest`）。CI は独立ワークフロー `e2e-tests.yml` で回す。ローカルは必要時に `./gradlew e2eTest`。網羅はここで広げず内側リングで担保する（ピラミッドの底を厚く）。

## replay（帰納的検証ハーネス・ゲート外）

規程 PDF との照合＝演繹的検証を補うため、**実在馬の公開記録を繁殖ワークフローへ逆算入力して一周駆動し、不変条件で弾かれた実在インスタンスを収集する**帰納的検証を置く（studbook。専用ソースセット `src/replay`、`./gradlew replay` で `build/reports/replay/reconciliation.md` を生成）。探索的な駆動がゲートを揺らさないよう **`check` / Kover / ArchUnit のいずれの対象にもしない**。

同型のハーネスを増やすときも次の契約を守る。

- **停止は失敗にしない**。分岐地点への到達は `assumeTrue` で skip として可視化する（assert にすると将来ドメインを直した瞬間に赤くなる）。
- **「停止ゼロ ＝ モデルが事実に耐えた」ではない**。綻びは停止ではなく**フィクスチャ側の合成（＝事実の捏造）に吸収されて静かに通る**ぶん、質が悪い。したがってフィクスチャは `facts`（公開事実・出典 URL つき）/ `synthesized`（合成値＋理由の `notes`）の **2 層**に構造分離し、レポート冒頭に「一周完了 ≠ 耐えた」の但し書きと、馬ごとの「合成した項目」を必ず出す。
- **自己言及するアサートを書かない**。`assert(loadAll().size == manifestNames().size)` は `loadAll = manifestNames().map(::load)` なら構造上ぜったい落ちないトートロジー。「JSON を置いたが manifest に書き忘れた死にデータ」を検出したいなら、クラスパス上の `fixtures/*.json` の集合と manifest の集合を突合する。

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
`domain.racing.model.race` / `domain.racing.service` / `e2e`（E2E テスト基盤）/ `dbdoc`（tbls ドキュメント生成基盤）/ `replay`（帰納的検証ハーネス）。

- **ゲート外のソースセットを新設したら、そのパッケージを `variant("mature")` の `excludes` に加える**。Kover は main 以外のソースセットも計測対象に取り込むため、加えないとテスト基盤・ツール基盤のコードが未カバレッジのままゲート母集団に乗り、成熟ゲートを割る（`e2e` / `dbdoc` / `replay` はいずれもこの理由で除外している）。

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

**診断メッセージのラムダを単独行に折らない**（ktfmt × diff-cover）。`checkNotNull(x) { "診断メッセージ" }` のように正常系では通らない分岐へメッセージのラムダを添えるとき、ktfmt が行長の都合でラムダ本体を単独行へ折ると、**壊れたデータでしか実行されない行が独立した行として計上され、常に未カバーになる**。列は先にローカル変数へ取り出し、`checkNotNull` の呼び出しごと 1 行に収めること（先例: `JdbcBloodHorseQueries.toOrigin()`）。

```kotlin
// 悪い例: ラムダ本体が単独行に折られ、その行が常に未カバーになる
sireId = BloodHorseId(checkNotNull(getObject("sire_id", UUID::class.java)) {
    "内国産の父IDが欠落: id=$id"
})

// 良い例: 列を先に取り出し、checkNotNull の呼び出しごと 1 行に収める
val sire = getObject("sire_id", UUID::class.java)
sireId = BloodHorseId(checkNotNull(sire) { "内国産の父IDが欠落: id=$id" })
```

[#687](https://github.com/ptiringo/toy-box/issues/687) では読み取り経路（`JdbcBloodHorseQueries` / `JdbcBreedingRegistrationQueries` / `JdbcBreedingResultQueries`）の NULL 診断がこの形で折られ、差分カバレッジが **89%** まで落ちて `--fail-under 90` を割った。1 行に収め直して **98%** へ回復している。同じ診断を持つ書き込み側（`JdbcBloodHorseRepository`）が 100% だったのは、たまたま 1 行に収まっていたからで設計の差ではない。**同じコードでも整形結果でゲートの通過可否が変わる**のが要点。

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
- `domain.racing.model`（`race`）: 探索段階のモデル。`excludes` 在籍（`sakamichi` は #367、`tennis` は #677 でテストが揃いゲート対象へ昇格済み）。

このうち `excludes` に在籍している探索領域（`racing.model.race` / `racing.service`）は、テストが揃った時点で `variant("mature")` の `excludes` から外してゲート対象へ昇格させる（`infrastructure.*` は既にゲート対象なので対象外）。
