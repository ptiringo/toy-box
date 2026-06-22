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
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation(libs.java.uuid.generator)
    implementation(libs.kotlin.result)
    implementation(libs.jmolecules.ddd)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jmolecules.archunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // プロジェクト固有の detekt カスタムルール（domain/application で throw しない 等）を detekt 実行時に組み込む
    detektPlugins(project(":detekt-rules"))
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

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
                        "com.example.api.domain.horseracing.model.jockey",
                        "com.example.api.domain.horseracing.model.horse.bloodhorse",
                        "com.example.api.domain.horseracing.service.horse",
                        "com.example.api.application.horseracing",
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
