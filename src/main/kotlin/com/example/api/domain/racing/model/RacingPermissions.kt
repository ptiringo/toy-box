package com.example.api.domain.racing.model

import com.example.api.domain.shared.Permission

/** 競馬（racing）コンテキストの権限語彙。値は `iam.role_permission.permission` と一致する契約。 */
object RacingPermissions {
    val JOCKEY_REGISTER = Permission("racing:jockey:register")
}
