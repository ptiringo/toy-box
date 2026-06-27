package com.example.api.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.library.dependencies.SliceAssignment
import com.tngtech.archunit.library.dependencies.SliceIdentifier

/**
 * アーキテクチャ規約テスト群（`com.example.api.architecture`）で共有する部品。
 *
 * 関心ごとに分割した `〜RulesTest` クラス（[OnionLayerRulesTest] / [DomainModelingRulesTest] /
 * [ControllerContractRulesTest] / [BoundedContextRulesTest] / [GeneralCodingRulesTest]）が共通で参照する
 * レイヤー定数・述語・スライス割り当てを 1 箇所へ集約する。規約そのものは各 `〜RulesTest` 側で `@ArchTest` として宣言し、 本ファイルは判定材料のみを提供する。
 */
internal const val DOMAIN = "com.example.api.domain.."
// 共有カーネル（building block 基盤）。最内核としてドメインモデルリングに含める。
internal const val DOMAIN_SHARED = "com.example.api.domain.shared.."
// 各コンテキストのドメインモデル（Entity / Value Object / Repository ポート）。
internal const val DOMAIN_MODEL = "com.example.api.domain..model.."
// 各コンテキストのドメインサービス（モデルを組み合わせる、フレームワーク非依存のロジック）。
internal const val DOMAIN_SERVICE = "com.example.api.domain..service.."
internal const val APPLICATION = "com.example.api.application.."
internal const val CONTROLLER = "com.example.api.controller.."
internal const val INFRASTRUCTURE = "com.example.api.infrastructure.."
// MCP アダプタ（Model Context Protocol 公開層）。adapter リングに属し、application 層を利用する。
internal const val MCP = "com.example.api.mcp.."

/**
 * ドメインサービスの失敗バリアント型（`〜Error` の sealed interface とその variant）であること。
 *
 * サービスの戻り値（`Result<_, 〜Error>`）の失敗側を表す型はサービスと同じパッケージに同居させており、 これは `object` / `class`
 * によるサービスのラップではないため [OnionLayerRulesTest.domainServicesAreTopLevelFunctions] の対象から除外する。ネストした
 * variant も囲みクラスを辿って判定する。
 */
internal val isFailureVariantType =
    DescribedPredicate.describe<JavaClass>("ドメインサービスの失敗バリアント型（〜Error）") { javaClass ->
        generateSequence(javaClass) { it.enclosingClass.orElse(null) }
            .any { it.simpleName.endsWith("Error") }
    }

/**
 * ドメイン層（`com.example.api.domain..`）に属する enum であること。
 *
 * HTTP 契約の DTO がフィールド型にドメイン enum を持つことを禁じる [ControllerContractRulesTest.dtosDoNotExposeDomainEnums]
 * で用いる。 enum かつドメインパッケージ配下のものだけを 対象とし、`controller` 層の契約専用 enum（`〜Dto`）は対象外とする。
 */
internal val isDomainEnum =
    DescribedPredicate.describe<JavaClass>("ドメイン層の enum") { javaClass ->
        javaClass.isEnum && javaClass.packageName.startsWith("com.example.api.domain.")
    }

/**
 * Kotlin コンパイラが生成する合成クラス（`when` の `〜Kt$WhenMappings`・ラムダ等）であること。
 *
 * controller のパッケージ allowlist（[ControllerContractRulesTest.requestSubpackageContainsOnlyRequests] /
 * [ControllerContractRulesTest.problemSubpackageContainsOnlyProblemMappers]）は**ソース上の宣言**を対象とするため、
 * 名前規約を満たしようがない合成クラスは判定対象から除外する。
 */
internal val isSyntheticClass =
    DescribedPredicate.describe<JavaClass>("合成クラス") { javaClass ->
        javaClass.modifiers.contains(JavaModifier.SYNTHETIC)
    }

/**
 * 境界づけられたコンテキスト（studbook / racing / sakamichi / tennis 等）へのスライス割り当て。
 *
 * application / domain / infrastructure 各層の直下のパッケージ名をコンテキスト名とみなす。 domain
 * 直下の共有カーネル（`shared`、[com.example.api.domain.shared.Command] 等）、controller、
 * ルートパッケージはコンテキストに属さないため対象外とする。
 */
internal object BoundedContextAssignment : SliceAssignment {
    private val contextPackage =
        Regex("""com\.example\.api\.(?:application|domain|infrastructure)\.([^.]+)(?:\..*)?""")

    override fun getIdentifierOf(javaClass: JavaClass): SliceIdentifier {
        val context = contextPackage.matchEntire(javaClass.packageName)?.groupValues?.get(1)
        return if (context == null || context == "shared") {
            SliceIdentifier.ignore()
        } else {
            SliceIdentifier.of(context)
        }
    }

    override fun getDescription(): String = "境界づけられたコンテキスト"
}
