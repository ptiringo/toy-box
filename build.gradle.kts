plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.power.assert)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.springdoc.openapi.gradle)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

group = "com.example"

version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    implementation(platform(libs.springdoc.openapi.bom))
    implementation(platform(libs.jmolecules.bom))
    implementation(platform(libs.spring.ai.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    // MCP インターフェース（REST と並ぶ adapter）。Spring AI 2.0 の MCP server を WebMVC(SSE/Streamable)
    // トランスポートで配線する。@McpTool 注釈付き Bean を annotation scanner が自動登録する。採否は ADR-0035。
    implementation(libs.spring.ai.starter.mcp.server.webmvc)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 永続化アクセス（Spring Data JDBC + Flyway）。集約 write は Spring Data JDBC（集約 = 永続化境界）。
    // 本番ランタイムの datasource は Prisma Postgres（実 PostgreSQL v17）へ配線する（#451 / ADR-0044）。
    // ローカル/CI/smoke/テストは全て PostgreSQL（組み込み H2 は cutover で全面脱却済み）。永続化の契約
    // テストは本番ターゲットの PostgreSQL を Testcontainers で用意して検証する（ADR-0027 / #422）。
    implementation(libs.spring.boot.starter.data.jdbc)
    // Flyway は starter で引く。Spring Boot 4 は autoconfig を機能別モジュールに分割しており、
    // FlywayAutoConfiguration は spring-boot-autoconfigure ではなく専用モジュール spring-boot-flyway
    // に移った。素の flyway-core だけだと autoconfig が classpath に無く、エラーも出さず migrate が
    // 走らない（#421）。starter-flyway が spring-boot-flyway(autoconfig) + spring-boot-jdbc +
    // flyway-core を引き込む。
    implementation(libs.spring.boot.starter.flyway)
    // ローカル開発（bootRun）時に compose.yaml の PostgreSQL を自動起動し datasource を自動配線する。
    // developmentOnly のため本番イメージ（bootBuildImage）・テスト classpath には載らない（#451）。
    developmentOnly(libs.spring.boot.docker.compose)
    // PostgreSQL ドライバと Flyway の PostgreSQL モジュール（Flyway 10+ は DB 別サポートをモジュール分割）。
    // 本番ランタイムは Prisma Postgres（実 PostgreSQL v17）へ接続するため runtimeOnly で本番 jar に載せる
    // （#451 / ADR-0044）。契約テスト（Testcontainers）でも runtimeOnly は testRuntimeClasspath に含まれる。
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation(libs.java.uuid.generator)
    implementation(libs.kotlin.result)
    implementation(libs.jmolecules.ddd)
    implementation(libs.jmolecules.events)
    implementation(libs.jmolecules.cqrs.architecture)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jmolecules.archunit)
    // 永続化の契約テストは Testcontainers(PostgreSQL) で本番ターゲット DB に対して検証する（ADR-0027 / #422）。
    // コンテナはシングルトン起動し接続先を @DynamicPropertySource（spring-test）で注入するため、
    // @ServiceConnection 用の spring-boot-testcontainers や JUnit5 拡張モジュールは要らず postgresql モジュール
    // 1 本でよい（core は推移取得）。アーティファクト ID は Testcontainers 2.0 の testcontainers-<module> 体系。
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // プロジェクト固有の detekt カスタムルール（domain/application で throw しない 等）を detekt 実行時に組み込む
    detektPlugins(project(":detekt-rules"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        // コンパイラ警告をエラー扱いにして混入をビルドで止める（警告ゼロ運用）。
        // detekt / ArchUnit / kover と同列の機械強制ゲート。個別に許容する場合は
        // @Suppress か -Xwarning-level=<ID>:warning で逃がす。
        allWarningsAsErrors = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // maxParallelForks（テスト JVM の並列フォーク）は意図的に既定（1）のまま据え置く。
    // 計測上、単一モジュール・小規模スイートの本プロジェクトでは forks を増やすと逆に遅くなる
    // （42s→97s @ forks=4）。コンテキストキャッシュ統計の実測では distinct な ApplicationContext は
    // 6 個のみ・ヒット率 ~99%（hit 525 / miss 6）で、:test の時間は「一度きりの 6 コンテキスト構築 +
    // JVM ウォームアップ(~7.8s)」が支配的。Spring のキャッシュは JVM 単位のためフォークすると 6 構築が
    // 各 JVM で重複し JVM 起動も N 倍になる。JVM 内スレッド並列も @MockkBean(springmockk＝@MockBean 機構)が
    // Spring 公式「Parallel Test Execution」の非推奨条件に該当するため不可。詳細・根拠は ADR-0015 / #349。
    // #338 で DB 導入後にテスト隔離を整えるか統合テストが多数になったら再評価する。
    // ユビキタス言語カタログの再生成フラグ（-DubiquitousLanguage.update=true）をフォークした JVM へ引き渡す。
    // UbiquitousLanguageCatalogTest が docs/ubiquitous-language.md の自動生成ブロックを書き戻すために参照する。
    System.getProperty("ubiquitousLanguage.update")?.let {
        systemProperty("ubiquitousLanguage.update", it)
    }
}

ktfmt {
    // Kotlin 公式コーディング規約準拠（4 space indent / 100 char limit）
    kotlinLangStyle()
}

detekt {
    // 雛形を上書きする形で `config/detekt/detekt.yml` を適用する
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // フォーマット系の自動修正は ktfmt が担当するため detekt 側では無効化する
    autoCorrect = false
    parallel = true
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        // checkstyle が detekt 2.x の XML 互換レポート
        checkstyle.required.set(true)
        sarif.required.set(true)
        markdown.required.set(true)
    }
}

kover {
    // 検証ゲート専用に total と同一内容の variant を複製する。
    // Kover 0.9 の検証ルールはパッケージ単位のフィルタを持てないため、
    // 「全体を見せるレポート（total）」と「成熟パッケージだけを検証する variant（mature）」を分ける。
    currentProject {
        copyVariant("mature", "jvm")
        // E2E（Karate）は check/pre-push に載せない設計のため、Kover 計測からも外す。
        // これにより koverGenerateArtifactJvm が e2eTest に依存せず、check から e2eTest が除外される。
        instrumentation { disabledForTestTasks.add("e2eTest") }
    }

    reports {
        // 全レポート共通の除外。カバレッジ対象として意味を持たないものだけを外す。
        filters {
            excludes {
                // エントリーポイント（main / Spring ブートストラップ）はカバレッジ対象外
                classes("com.example.api.ApiApplication*")
            }
        }

        // total: コードの全体像を見せるレポート（穴の可視化が目的なので絞り込まない）。
        total {
            xml {
                // CI で集計するため XML を常に生成する
                onCheck = false
            }
            html {
                title = "toy-box カバレッジ"
                onCheck = false
            }
            // koverLog: CI の Job Summary に流すための 1 行集計（外部 Action 不要）
            log {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                format = "全体（探索領域含む）の行カバレッジ: <value>%"
            }
            // total はルールを持たない可視化専用レポート。検証ゲートは mature variant が担うため、
            // total の verify を check から外す。これを残すと check 実行時に koverVerify(total) と
            // koverVerifyMature が並列で走り、copyVariant で複製した両 variant が中間成果物を共有する
            // 結果、total が先に走ると mature の検証がフィルタ前（全体）の値を掴んで誤検知する
            // （Linux 等でタスク順が入れ替わると顕在化するレース）。
            verify { onCheck = false }
        }

        // mature: 検証ゲートを「成熟領域のみ」に絞る。
        // includes 列挙ではなく excludes 反転: 全体をゲートし、探索段階のパッケージだけ明示除外する。
        // 新パッケージは既定でゲート対象に入り、除外し忘れても「より厳しくなる」安全側に倒れる。
        variant("mature") {
            filtersAppend {
                excludes {
                    packages(
                        // 探索段階（レイヤーごとのテスト未整備）。成熟したらこの行を外す＝ゲート対象へ昇格。
                        "com.example.api.domain.racing.model.race",
                        "com.example.api.domain.racing.service",
                        "com.example.api.domain.tennis",
                        // e2eTest ソースセットのクラス（Karate ランナー等）を除外。
                        // アプリケーションロジックではなくテスト基盤コードのため。
                        "com.example.api.e2e",
                        // dbdoc ソースセットの tbls ドキュメント生成ロジックを除外。
                        // アプリケーションロジックではなくツール基盤コードのため。
                        "com.example.api.dbdoc",
                    )
                }
            }
            // diff-cover に食わせる excludes 適用済み XML。onCheck=false で check には載せない
            // （生成は CI/ローカルで明示タスク実行する）。
            xml { onCheck = false }
            log {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                format = "成熟ゲート対象の行カバレッジ（分岐も別途ゲート）: <value>%"
            }
            verify {
                onCheck = true
                rule("成熟領域の行・分岐カバレッジ（リグレッション防止のラチェット）") {
                    bound {
                        // 反転後母集団の実測 94.57% を 90 に固定したラチェット。
                        minValue = 90
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                        aggregationForGroup =
                            kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                    }
                    bound {
                        // 反転後母集団の実測 84.91% を 80 に固定したラチェット。
                        minValue = 80
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                        aggregationForGroup =
                            kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                    }
                }
            }
        }
    }
}

// --- E2E（Karate によるブラックボックス API テスト） ---
// 専用ソースセットに隔離する。ArchUnit は src/test のみ走査し、Kover は test タスクに紐づくため、
// E2E は規約検査・カバレッジゲートのいずれの対象にもならない（探索的な E2E がゲートを揺らさない）。
sourceSets {
    create("e2eTest") {
        // main の出力を載せることで @SpringBootApplication などのアプリ設定クラスをスキャンできる。
        // PostgresContainerSupport（src/test）を再利用するため test の出力もクラスパスへ載せる。
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

// e2eTest の依存は test の依存（spring-boot-starter-test / testcontainers-postgresql 等）を引き継ぐ。
configurations["e2eTestImplementation"].extendsFrom(configurations["testImplementation"])

configurations["e2eTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies { "e2eTestImplementation"(libs.karate.core) }

// CI 独立ジョブ専用のタスク。check / pre-push には意図的に載せない（速い内側ループを保つ）。
val e2eTest by
    tasks.registering(Test::class) {
        description = "Karate によるブラックボックス API E2E テスト（CI 独立ジョブ専用。check/pre-push には載せない）"
        group = "verification"
        testClassesDirs = sourceSets["e2eTest"].output.classesDirs
        classpath = sourceSets["e2eTest"].runtimeClasspath
        shouldRunAfter(tasks.named("test"))
    }

// --- DB スキーマドキュメント生成（tbls, #447） ---
// 専用ソースセットに隔離する（ArchUnit は src/test のみ走査、Kover は test タスク紐付けのため、
// 生成エントリポイントは規約検査・カバレッジゲートのいずれの対象にもならない）。
sourceSets {
    create("dbdoc") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

// dbdoc の依存は test の依存（testcontainers-postgresql / flyway-database-postgresql 等）を引き継ぐ。
configurations["dbdocImplementation"].extendsFrom(configurations["testImplementation"])

configurations["dbdocRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// tbls 1.94.5 は cwd の .tbls.yml を自動探索しないため、設定ファイルの絶対パスをシステムプロパティで
// 明示的に渡し、生成側が --config で読み込む。docPath（相対 dbdoc）が repo root 直下へ解決されるよう
// 作業ディレクトリも projectDir に固定する。
val tblsConfigFile = layout.projectDirectory.file(".tbls.yml").asFile

// tbls は mise(aqua backend) 管理で素の PATH に乗らないため、実行時 PATH の活性化に依存せず
// 確実に解決できるよう、設定時に `mise which tbls` で絶対パスを引き、システムプロパティ tbls.bin で
// 生成側へ渡す（CI は mise-action 活性化済み、ローカルは mise インストール済みなら解決できる）。
// providers.exec は Configuration Cache 互換。.get() は各 dbdoc タスクの構成時に評価し、
// tbls 解決が不要な `check` 等では `mise which tbls` が走らないようにする。
val tblsBin =
    providers.exec { commandLine("mise", "which", "tbls") }.standardOutput.asText.map(String::trim)

// dbdoc/ を再生成する（開発者が手動実行し差分をコミットする）。
tasks.register<JavaExec>("generateDbDoc") {
    description = "Testcontainers の PostgreSQL に Flyway を適用し tbls で dbdoc/ を再生成する"
    group = "documentation"
    classpath = sourceSets["dbdoc"].runtimeClasspath
    mainClass = "com.example.api.dbdoc.DbDocGeneratorKt"
    workingDir = layout.projectDirectory.asFile
    systemProperty("tbls.config", tblsConfigFile.absolutePath)
    systemProperty("tbls.bin", tblsBin.get())
    args("generate")
}

// dbdoc/ が最新か（tbls diff）とコメント規約（tbls lint）を検査する。
// check / pre-push には載せない（Docker 依存の重いタスクを内側ループから外す。CI 専用ジョブで回す）。
tasks.register<JavaExec>("checkDbDoc") {
    description = "tbls diff（ドキュメント鮮度）と tbls lint（コメント必須）でスキーマドリフト/規約を検査する"
    group = "verification"
    classpath = sourceSets["dbdoc"].runtimeClasspath
    mainClass = "com.example.api.dbdoc.DbDocGeneratorKt"
    workingDir = layout.projectDirectory.asFile
    systemProperty("tbls.config", tblsConfigFile.absolutePath)
    systemProperty("tbls.bin", tblsBin.get())
    args("check")
}

// --- OpenAPI 仕様の書き出しと lint（#327） ---
// generateOpenApiDocs はアプリを forked bootRun でバックグラウンド起動し、/v3/api-docs を
// build/openapi.json へ書き出す（出力先・ファイル名はプラグインのデフォルト）。datasource は
// ローカル bootRun と同じく spring-boot-docker-compose が compose.yaml の PostgreSQL を
// 自動供給する（Docker が必要）。生成物は build 成果物でありコミットしない（ADR-0054）。
// ローカル開発中のアプリ（8080）と衝突しないよう forked 起動は専用ポート 8090 を使う。
openApi {
    apiDocsUrl.set("http://localhost:8090/v3/api-docs")
    // CI のコールドスタート（PostgreSQL イメージ pull + Flyway 適用 + アプリ起動）を見込んで
    // デフォルト 30 秒から延長する。
    waitTimeInSeconds.set(120)
    customBootRun {
        args.set(listOf("--server.port=8090"))
        // forked プロセスの既定 workingDir（build/tmp/forkedSpringBootRun）には compose.yaml が無く、
        // spring-boot-docker-compose がそこを探して見つからず起動失敗する。プロジェクトルートを指定して
        // ローカル bootRun と同じく compose.yaml を発見できるようにする。
        workingDir.set(layout.projectDirectory)
    }
}

// vacuum は mise(aqua backend) 管理で素の PATH に乗らないため、tbls（tblsBin）と同じ流儀で
// `mise which vacuum` により絶対パスを解決する（タスク実現時に評価され、不要なタスクでは走らない）。
val vacuumBin =
    providers
        .exec { commandLine("mise", "which", "vacuum") }
        .standardOutput
        .asText
        .map(String::trim)

// 生成済みの OpenAPI 仕様を vacuum で lint する。生成（アプリ起動・Docker 依存）が重いため
// check / pre-push には載せず、CI の独立ワークフロー（openapi-lint.yml）専用とする
// （checkDbDoc / e2eTest と同じ扱い。ADR-0054）。
tasks.register<Exec>("lintOpenApiDocs") {
    description = "OpenAPI 仕様を生成し vacuum で lint する（CI 専用。check には載せない）"
    group = "verification"
    dependsOn("generateOpenApiDocs")
    workingDir = layout.projectDirectory.asFile
    commandLine(
        vacuumBin.get(),
        "lint",
        "--ruleset",
        "config/vacuum/ruleset.yaml",
        "--ignore-file",
        "config/vacuum/ignore.yaml",
        "--ignore-polymorph-circle-ref",
        "--fail-severity",
        "warn",
        "--details",
        "--no-banner",
        "build/openapi.json",
    )
}

// springdoc-openapi-gradle-plugin（gradle-execfork-plugin 経由）は Task を直接プロパティとして
// 保持するため Configuration Cache と非互換（org.gradle.api.Task はシリアライズ不可）。
// org.gradle.configuration-cache.problems=fail のもとでは通常のビルドが丸ごと失敗するため、
// 関連タスクを個別に非対応と宣言し、これらのタスクだけ CC を使わず実行させる。
listOf("forkedSpringBootRun", "generateOpenApiDocs", "forkedSpringBootStop").forEach { taskName ->
    tasks.named(taskName) {
        notCompatibleWithConfigurationCache(
            "springdoc-openapi-gradle-plugin(gradle-execfork-plugin) が Task を直接保持し CC 非対応のため"
        )
    }
}
