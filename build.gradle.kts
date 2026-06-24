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

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 永続化アクセス（Spring Data JDBC + Flyway）。集約 write は Spring Data JDBC（集約 = 永続化境界）。
    // ランタイムの datasource は当面 H2（PostgreSQL 互換モード）の組み込み DB のまま据え置く。実リポジトリは
    // まだ InMemory Bean のため datasource/Flyway は付随的に初期化されるだけで、本番 PostgreSQL 化（Cloud SQL
    // 配線）は実永続化を使う #423 / インフラ作業に委ねる。一方、永続化の契約テストは本番ターゲットの
    // PostgreSQL を Testcontainers で用意して検証する（ADR-0027 / #422。testing.md の宿題に対応）。
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    // Flyway は starter で引く。Spring Boot 4 は autoconfig を機能別モジュールに分割しており、
    // FlywayAutoConfiguration は spring-boot-autoconfigure ではなく専用モジュール spring-boot-flyway
    // に移った。素の flyway-core だけだと autoconfig が classpath に無く、エラーも出さず migrate が
    // 走らない（#421）。starter-flyway が spring-boot-flyway(autoconfig) + spring-boot-jdbc +
    // flyway-core を引き込む。
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("com.h2database:h2")
    // PostgreSQL ドライバと Flyway の PostgreSQL モジュール（Flyway 10+ は DB 別サポートをモジュール分割）は
    // 契約テスト（Testcontainers）でのみ要るため testRuntimeOnly に置く。本番 jar・ランタイムには載らない。
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation(libs.java.uuid.generator)
    implementation(libs.kotlin.result)
    implementation(libs.jmolecules.ddd)
    implementation(libs.jmolecules.events)
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
    testImplementation("org.testcontainers:testcontainers-postgresql")
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
    currentProject { copyVariant("mature", "jvm") }

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

        // mature: 検証ゲートを「成熟パッケージのみ」に絞る。
        // 探索段階のモデル（tennis / sakamichi / breeding / race / racehorse / stallion 等）は
        // total レポートには出すが、ゲートからは外してノイズで CI を赤くしない。
        variant("mature") {
            // 共通の除外に加え、成熟＝レイヤーごとのテストが揃っている領域だけを includes で残す
            filtersAppend {
                includes {
                    packages(
                        "com.example.api.domain.shared",
                        "com.example.api.domain.racing.model.jockey",
                        "com.example.api.domain.studbook.model.horse.bloodhorse",
                        "com.example.api.domain.studbook.service.horse",
                        "com.example.api.application.studbook",
                        "com.example.api.application.racing.jockey",
                        "com.example.api.controller",
                    )
                }
            }
            log {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                format = "成熟ゲート対象の行カバレッジ: <value>%"
            }
            verify {
                // check 実行時に検証も走らせる
                onCheck = true
                rule("成熟パッケージの行カバレッジ（リグレッション防止のラチェット）") {
                    bound {
                        // 現状実測 88.3%（302/342 行）を 85% に固定し、以後の低下を検出するラチェット。
                        // 成熟領域に新コードを足すならテストも添えること、という圧をかける。
                        minValue = 85
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                        aggregationForGroup =
                            kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                    }
                }
            }
        }
    }
}
