package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex

/**
 * 繁殖登録によって個体に付与されるロール。
 *
 * 繁殖登録（JAIRS）は雄雌共通の単一の登録で、繁殖登録証明書の `性`（雄/雌）によって担うロールが決まる
 * （繁殖登録のながれ・繁殖登録証明書見本＝種牡馬ディープインパクトも同一様式）。雄なら種牡馬（[STALLION]）、 雌なら繁殖牝馬（[BROODMARE]）。
 */
enum class BreedingRole {
    /** 種牡馬（繁殖の用に供する雄馬） */
    STALLION,

    /** 繁殖牝馬（繁殖の用に供する雌馬） */
    BROODMARE;

    companion object {
        /** 性から繁殖ロールを定める。雄=種牡馬・雌=繁殖牝馬。 */
        fun from(sex: Sex): BreedingRole =
            when (sex) {
                Sex.MALE -> STALLION
                Sex.FEMALE -> BROODMARE
            }
    }
}
