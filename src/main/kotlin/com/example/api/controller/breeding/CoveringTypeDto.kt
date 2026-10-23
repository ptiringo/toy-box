package com.example.api.controller.breeding

/** 種付の区分（HTTP 契約）。国内種付と海外種付で種牡馬の参照のしかたと証明のしかたが異なる。 */
enum class CoveringTypeDto {
    /** 国内種付（日本で繁殖登録された種牡馬との種付。種牡馬は `stallion_id` で参照する）。 */
    DOMESTIC,

    /** 海外種付（外国で外国種牡馬と行われた種付。種牡馬は `foreign_sire` に属性で持つ）。 */
    OVERSEAS,
}
