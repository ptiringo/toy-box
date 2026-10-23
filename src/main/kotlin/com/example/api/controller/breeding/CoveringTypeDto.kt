package com.example.api.controller.breeding

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 種付の区分（HTTP 契約）。国内種付と海外種付で種牡馬の参照のしかたと証明のしかたが異なる。
 *
 * 種付せずの年は null になるため、応答では nullable な enum として出る。OpenAPI 3.1 でインラインの nullable enum は `enum` に `null`
 * を含めないと矛盾する（vacuum `nullable-enum-contains-null`）ので、`enumAsRef` で独立したスキーマにし、 参照側を `oneOf:
 * [$ref, null]` の形で出す。
 */
@Schema(description = "種付の区分", enumAsRef = true)
enum class CoveringTypeDto {
    /** 国内種付（日本で繁殖登録された種牡馬との種付。種牡馬は `stallion_id` で参照する）。 */
    DOMESTIC,

    /** 海外種付（外国で外国種牡馬と行われた種付。種牡馬は `foreign_sire` に属性で持つ）。 */
    OVERSEAS,
}
