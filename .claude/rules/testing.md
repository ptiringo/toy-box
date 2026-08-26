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

- 要るのは **`.kt` / `.kts` / `.java` を含む push のとき**だけ（両コマンドの `glob`）。ドキュメントや設定だけの push はゲートごとスキップされ、Docker を落としていても通る。glob を当てる対象は `scripts/list-push-target-files.sh` が供給する（lefthook 既定の `{push_files}` は worktree で比較対象を取り違え、`.md` だけの push でもゲートが起動していた。#804）。
- pre-push の先頭で `scripts/check-docker-available.sh` が Docker 到達性を確認し、駄目なら理由と対処を出して即座に落とす（`piped: true` で `full-test` は走らない）。ハングして「push が無反応」に見える状態を潰すためのガードで、ゲートの範囲は変えない。
- 判定は `docker info` の成否のみ。**Docker は生きているが Testcontainers だけ失敗する**ケース（イメージの pull 不可・リソース枯渇等）はガードを素通りし、従来どおりテストの失敗として出る。
- Docker が復旧しないまま push したいときは **`LEFTHOOK_EXCLUDE=docker-available,full-test git push`**（`--no-verify` は gitleaks 等まで飛ばすので最後の手段）。同じテストは CI（`api-tests.yml`）で走る。

## テスト実行性能（コンテキストキャッシュ優先・並列化しない）

Spring テストの主コストは `ApplicationContext` の構築。速度の本筋は**並列化ではなくコンテキストキャッシュの再利用最大化**にある（実測の根拠は [ADR-0015](../../docs/adr/0015-gradle-build-performance-tuning.md)）。

- **distinct なコンテキスト構成を増やさない**。キャッシュは「同一の unique 構成」のときだけ再利用される（キーは classes / context customizers / active profiles / property sources 等の組合せ）。`@MockkBean` は context customizer を足してキーを分けるので**乱発しない**、`@Import` 構成は揃える、`@SpringBootTest(webEnvironment=...)` を不必要に散らさない。
- **`@DirtiesContext` は原則使わない**（キャッシュを退避させ再構築を強いる）。状態リークは設計で断つ。
- **テストは JVM 内のクラス間並列で走る**（[ADR-0079](../../docs/adr/0079-in-jvm-class-level-test-parallelism.md) / #690。下記）。ただし**`maxParallelForks`（プロセス並列）は採らない**。キャッシュが JVM 単位のため、フォークすると 16 個のコンテキスト構築が各 JVM で重複する（ADR-0015 当時 forks=1/2/4 で 42s/61s/97s と単調悪化）。
- 速度を縮めたいときの効く順: ビルドキャッシュ/デーモン（[ADR-0015](../../docs/adr/0015-gradle-build-performance-tuning.md)）→ コンテキスト構成の共通化（#817 で実施済み。下記）→ クラス間並列（#690 で実施済み。下記）。

### web 環境を要する `@SpringBootTest` は 1 構成に揃える（崩すと -20.5% が消える）

`ApiApplicationTests` / `McpDisabledByDefaultTest` / `HealthEndpointTest` / `OpenApiTest` / `SecurityConfigTest` の 5 クラスは、**`RANDOM_PORT` + `@AutoConfigureRestTestClient` + `@Import(TestJwtDecoderConfiguration)` という同一キー**でコンテキストを 1 つ共有する（[ADR-0077](../../docs/adr/0077-consolidate-web-test-contexts-trading-jwt-decoder-assurance.md)）。CI 実測（各 3 回の中央値）で `:test` が 84.93s → 67.55s（**-20.5%**）になった構成であり、**どれか 1 つに `@MockkBean` / `@TestPropertySource` / 別の `@Import` を足すと、そのクラスだけ別コンテキストへ分岐して効果が失われる**。web 環境を要する `@SpringBootTest` を新しく足すときも、この 5 クラスと同じ構成に揃える。ArchUnit / detekt では強制できないのでレビューで担保する。

- `RestTestClient` も JWT も使わないクラス（`ApiApplicationTests` / `McpDisabledByDefaultTest`）が `@AutoConfigureRestTestClient` と `@Import` を持つのは、**キーを一致させるためだけ**。不自然に見えても外さない。
- 引き換えに「本番の `issuer-uri` 設定から `JwtDecoder` Bean が生成される」ことを確かめるコンテキストが無くなっている（API E2E もブラウザ E2E も decoder を差し替えるため、リポジトリ全体で担保が無い）。この穴は #813 が引き取る。
- **`@WebMvcTest` スライスの 11 個は削減対象にしない**。`controllers` 引数ごとに 1 コンテキストになるのはスライステストの機械的帰結で、束ねると「そのコントローラだけを載せる」意図が壊れる。
- **効果は推論せず実測で確かめる**。実測では 18→17 個で -3.7〜-6.3%、17→16 個で -14.1% と、1 個あたりの効果が 2 倍以上違った（機序は未特定）。ローカルは初回コンテキスト構築の振れが大きく（同一構成で 17.5s / 36.5s、外れ値では `:test` が 74 分停止）効果より測定ノイズが大きいため、**CI（`workflow_dispatch` で 3 回）で測る**。ただし **3 回で足りるのはこの -20.5% 規模の効果に対してだけ**で、10% 前後を測るなら各 8 回が要る（後述「`:test` の速度を測り直すときは各 8 回」）。

### クラス間並列（並列にしてはいけないテストは 2 種類だけ）

テストは**クラス間だけ並列**で走る（クラス内のメソッドは逐次のまま）。設定の出所は `build.gradle.kts` の `tasks.withType<Test>` にある `junit.jupiter.execution.parallel.*` の 3 行で、決定経緯は [ADR-0079](../../docs/adr/0079-in-jvm-class-level-test-parallelism.md)（CI -11.5% / ローカル -13.3%）。

並列にしてはいけないのは次の 2 種類だけで、どちらも `@Execution(ExecutionMode.SAME_THREAD)` で閉じる。

| 対象 | 理由 | 書き手がすること |
|---|---|---|
| DB を触るテスト | 全テーブル TRUNCATE（[ADR-0070](../../docs/adr/0070-db-test-cleanup-via-truncate-not-transactional.md)）が並行実行と両立せず、他スレッドのデータまで消す | **何もしなくてよい**。`PostgresContainerSupport` のクラス注釈が `@Inherited` で継承先すべてに効く |
| `@WebMvcTest` スライス | `@MockkBean`（`@MockBean` 機構）が Spring 公式「Parallel Test Execution」の非推奨条件に該当する | **クラスに `@Execution(SAME_THREAD)` を付ける** |

- **`@WebMvcTest` を新しく足すときは `@Execution(SAME_THREAD)` を忘れないこと**。付け忘れは例外にならず、非推奨条件のまま静かに走って後から不安定さとして出る。`TestParallelismRulesTest.webMvcTestsRunInSameThread` が機械強制するので `check` で落ちる（注釈の有無だけでなく**値が `SAME_THREAD` であること**まで見る。`CONCURRENT` の明示も付け忘れと同じ結果になるため）。
- **並列が効いていること自体を `ParallelExecutionProbeTest` が守る**。設定 3 行が消えても全テストは緑のまま通り速度が静かに戻るだけなので、2 クラスが互いの到達を待ち合わせ、逐次実行なら必ずタイムアウトして落ちるプローブを置いている。これが落ちたら、まず `build.gradle.kts` の並列設定を疑う。
- **`@AnalyzeClasses`（ArchUnit）の規約テストは並列化されない**。ArchUnit 独自の TestEngine で動くため `junit.jupiter.execution.parallel.*`（Jupiter engine の設定）の対象外になる。並列化されるのは自前で `ClassFileImporter` を回すメタテストのほう。
- **テストを並列で走らせると個々のクラスは遅くなる**（per-class 合計は約 2 倍）。それでも壁時計が縮むのは実行が重なるためで、per-class time を見て「遅くなった」と判断しないこと。

### `:test` の速度を測り直すときは各 8 回（3 回では符号が反転する）

10% 前後の効果は**各 3 回では判定できない**。#690 では最初の各 3 回で「+6.1% 遅い」、次の各 3 回で「-14.1% 速い」と符号が反転し、各 8 回に増やして初めて安定した（-11.5%, 並べ替え検定 p=0.030）。上記「効果は推論せず実測で確かめる」の CI 3 回という手順は -20.5% 規模の効果には足りたが、一般には足りないと考えること。

- **ローカルでも測れる**。baseline と parallel を**交互に**回してペアで比べれば、ランナー状態のドリフトが打ち消せる（#690 では 8 ペア中 7 ペアで符号が揃い、符号検定 p=0.035）。ADR-0077 の「ローカルは計測に使えない」は、単発の実行を 3 回だけ比べる前提の話。
- **初回の実行は捨てる**（ウォームアップで 1.3〜1.5 倍遅い）。外れ値（#818 の 400 秒級ブロック）を引いた回も別扱いにする。
- 壁時計は `--profile` レポートか、`:cleanTest :test --no-build-cache` を挟んだ前後の時刻で測る。`BUILD SUCCESSFUL in Xs` は compile 等を含むため使えない。

## E2E（ブラックボックス API テスト・ゲート外）

全層を実配線したまま HTTP 越しに叩く E2E は素の Spring ネイティブ（`RestTestClient`）で書く（`src/e2eTest`、決定は [ADR-0056](../../docs/adr/0056-drop-karate-native-resttestclient-e2e.md)。Karate は [ADR-0039](../../docs/adr/0039-e2e-api-tests-with-karate.md) で採用したが用途に対し過大なため撤退）。`@SpringBootTest(RANDOM_PORT)` + `@AutoConfigureRestTestClient` + Testcontainers PostgreSQL でアプリを起動し、`RestTestClient` でシナリオを流す（`HealthEndpointTest` と同型）。遅く探索的なため **ArchUnit / Kover / `check` / pre-push のいずれの対象にもしない**（独立ソースセット `e2eTest` + タスク `e2eTest`）。CI は独立ワークフロー `e2e-tests.yml` で回す。ローカルは必要時に `./gradlew e2eTest`。網羅はここで広げず内側リングで担保する（ピラミッドの底を厚く）。

## ブラウザ E2E（frontend・ゲート外）

`frontend/` の画面は vitest + jsdom（コンポーネント単位）に加えて、実ブラウザでの通しを 1 本だけ持つ
（`frontend/e2e/`、Playwright。#725。決定は [ADR-0076](../../docs/adr/0076-browser-e2e-playwright-auth-emulator-boottestrun.md)）。
API E2E と同じく **`check` / pre-push のゲート外**で、CI は独立ワークフロー `browser-e2e.yml` が回す。
ローカルは `cd frontend && npm run test:e2e`（Docker が要る）。人間向けの手順は `frontend/README.md` が出所。

- **守るのは「配線が繋がっていること」**（認証・ガード・ルーティング・API パス・世界スコープ）。分岐や
  エラー表示は jsdom 側で担保し、シナリオはハッピーパス 1 本に絞る。
- **認証は Firebase Auth Emulator を使う**。Emulator が出す ID トークンは未署名なので、`src/test` の
  `EmulatorJwtDecoder` が署名検証だけを省いて受理する（issuer / audience / 有効期限は本番と同じ validator を掛ける）。
- **バックエンドは `bootRun` ではなく `./gradlew bootTestRun`** で起動する。test runtime classpath を使うため、
  本番成果物（`src/main`）に署名検証を迂回するコードを一切入れずに済む。**`EmulatorJwtDecoder` を `src/main` へ
  移してはならない。**
- **射程外**: JWKS による署名検証、実 Identity Platform テナントとの疎通、WorldsPage の改名・削除、
  エラー分岐。「ブラウザ E2E が緑 ＝ 認証が安全」とは読まないこと。

### この基盤を触るときに踏む地雷（実測済み）

- **`bootTestRun` では `compose.yaml` の自動配線が効かない**。`spring-boot-docker-compose` は
  `developmentOnly` 依存（`build.gradle.kts` / #451）で **test runtime classpath に載らない**ため。
  DB は `docker compose up -d --wait` で先に立て、`SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` を
  env で明示供給する（本番 Cloud Run と同じ注入経路）。`bootRun` の感覚で「勝手に立つ」と思うと嵌まる。
- **`GCP_PROJECT_ID=toy-box-e2e` を渡し忘れると全トークンが 401 になる**。`application.yml` の issuer / audience
  は `${GCP_PROJECT_ID:...}` で既定値が実在しない値になっており（fail closed）、フロントの
  `VITE_FIREBASE_PROJECT_ID` / Emulator の `--project` と **3 箇所で一致**していないと通らない。
- **`vite preview` は既定で IPv6 `[::1]` にしか bind しない**。`127.0.0.1` を明示しないと
  `http://127.0.0.1:5173` で待つ側から到達できない。出所は `vite.config.ts` の `preview.host` 1 箇所で、
  起動コマンド側の `--host` と二重に持たせない。
- **`e2e/` と `playwright.config.ts` は `tsconfig.node.json` の `include` が型検査の入口**。`tsc` は
  プロジェクト参照を辿らないため、`npm run build` は `tsc -b` で両プロジェクトを検査する。node 側の
  ファイルを足したら `include` にも足すこと（Biome も Playwright も型は見ないので、漏れると無検査で残る）。
- **ローカルで `npm run dev` が 5173 に居座っていると、`reuseExistingServer` がそれを再利用する**。
  `--mode e2e` でないビルド（＝実 Identity Platform を向いた `.env.local`）が使われ、
  **画面は正常に見えるのに `.status` だけ 401** という最も分かりにくい形で落ちる。9099 / 8080 も同様。
- **停止に `pkill -f bootTestRun` は効かない**（Java プロセスのコマンドラインにその文字列が無い）。
  `lsof -iTCP:8080 -sTCP:LISTEN -P` で PID を引いて落とす。
- **CI の `retries` は 1、ローカルは 0**（`process.env.CI ? 1 : 0`）。共有ランナーの CPU 枯渇で
  Playwright のポーリングがタイムアウト設定を大きく超過して落ちることがあるため。ローカルで 0 なのは
  本物の不安定さを隠さないため。

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
- **カバレッジ単位は LINE と BRANCH の 2 ボーンド**。下限は実測直下のキリ番（**LINE 95% / BRANCH 85%**。出所は `build.gradle.kts` の `minValue`）に固定した**手動ラチェット**。実測が上がったら手で下限を上げてよい（[#735](https://github.com/ptiringo/toy-box/issues/735) で 90% / 80% から引き上げ。そのときの実測は LINE 97.35% / BRANCH 90.36%）。割ると `./gradlew check`（CI の `koverVerifyMature`）が失敗する。
- **自動ラチェット機構は持たない**（YAGNI）。手動で引き上げる。
- **引き上げるとき差分ゲート（diff-cover の `--fail-under`）は触らない**。差分ゲートの閾値は集約ゲートの下限に追従させず独立に決めると [#843](https://github.com/ptiringo/toy-box/issues/843) で確定した（理由は [ADR-0055](../../docs/adr/0055-patch-coverage-diff-cover-gate.md) 決定 2-1。母集団が PR ごとに数行〜百数十行しかなく、ラチェットの「実測直下に固定」という論理が成立しない）。

探索除外パッケージ（`build.gradle.kts` の `variant("mature")` の `excludes` が唯一の出所。ここは要約）:
`domain.racing.model.race` / `domain.racing.service` / `e2e`（E2E テスト基盤）/ `dbdoc`（tbls ドキュメント生成基盤）/ `replay`（帰納的検証ハーネス）。

- **ゲート外のソースセットを新設したら、そのパッケージを `variant("mature")` の `excludes` に加える**。Kover は main 以外のソースセットも計測対象に取り込むため、加えないとテスト基盤・ツール基盤のコードが未カバレッジのままゲート母集団に乗り、成熟ゲートを割る（`e2e` / `dbdoc` / `replay` はいずれもこの理由で除外している）。

集約ゲート（本見直し）は成熟領域全体の絶対水準を守り、patch coverage（[#437](https://github.com/ptiringo/toy-box/issues/437)）は新規・変更コードのカバレッジを別途課す補完関係にある。

### 差分ゲート（patch coverage・diff-cover）

集約ゲート（成熟領域全体の絶対水準）を補完し、PR で変更した行のカバレッジを個別に検証するのが差分ゲートである（[ADR-0055](../../docs/adr/0055-patch-coverage-diff-cover-gate.md)）。母集団が厚い集約ゲートだけでは新規変更行のテスト漏れが薄まって見逃されるため、変更行そのものに閾値を課す。

- **[diff-cover](https://github.com/Bachmann1234/diff-cover)**（pipx, 10.3.0, mise 管理）で変更行カバレッジを算出し、`--fail-under 90` で 90% 未満の PR を落とす（出所は `.github/workflows/api-tests.yml`。集約ゲートの LINE 下限とは**独立**の値で、ラチェットに連動しない）。
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

- `infrastructure.*`（JDBC リポジトリ）: `Jockey` は Testcontainers 契約テスト済み。残り集約（`BloodHorse` / `BreedingRegistration` / `BreedingResult`）は JDBC 実装＋契約テストが未整備で、移行に伴い InMemory を廃止する（JDBC 一本化。[ADR-0030](../../docs/adr/0030-jdbc-only-persistence-retire-inmemory.md) / #435）。なお `infrastructure.*` は `excludes` に入れておらず**既にゲート母集団内**。未テスト分は集計に乗るが現状の LINE 95% / BRANCH 85% を満たしている（割り込んだらテストを添えること）。
- `domain.racing.service`（`confirmRaceResult`）: サービスだがテスト無し。`excludes` 在籍。
- `domain.racing.model`（`race`）: 探索段階のモデル。`excludes` 在籍（`sakamichi` は #367、`tennis` は #677 でテストが揃いゲート対象へ昇格済み）。

このうち `excludes` に在籍している探索領域（`racing.model.race` / `racing.service`）は、テストが揃った時点で `variant("mature")` の `excludes` から外してゲート対象へ昇格させる（`infrastructure.*` は既にゲート対象なので対象外）。
