package com.example.api.domain.studbook.model.inspection

import org.jmolecules.ddd.annotation.ValueObject

/**
 * DNA 型による親子判定の結果。
 *
 * 血統登録では、申告された父・母との親子関係を DNA 型検査で確認する。矛盾がない（[CONSISTENT]）場合のみ血統登録できる。
 */
@ValueObject
enum class DnaParentageResult {
    /** 申告どおりの親子関係と矛盾しない。 */
    CONSISTENT,

    /** 申告された親子関係と矛盾する。 */
    INCONSISTENT,

    /** 未検査。 */
    UNTESTED,
}
