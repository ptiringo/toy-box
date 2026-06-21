package com.example.api.detekt

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * toy-box プロジェクト固有のカスタムルールセットを detekt に提供する [RuleSetProvider]。
 *
 * `META-INF/services/dev.detekt.api.RuleSetProvider` に登録され、detekt 実行時に ServiceLoader 経由で読み込まれる。
 * 設定ファイル（`config/detekt/detekt.yml`）では本ルールセットを [RULE_SET_ID]（`toy-box`）のキーで参照する。
 */
class ToyBoxRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId(RULE_SET_ID)

    override fun instance() =
        RuleSet(
            ruleSetId,
            mapOf<RuleName, (Config) -> Rule>(
                RuleName("NoThrowInDomainAndApplication") to ::NoThrowInDomainAndApplication
            ),
        )

    private companion object {
        const val RULE_SET_ID = "toy-box"
    }
}
