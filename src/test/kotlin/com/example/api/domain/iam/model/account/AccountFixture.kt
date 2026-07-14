package com.example.api.domain.iam.model.account

import com.github.michaelbull.result.unwrap

object AccountFixture {
    fun account(
        subjectId: SubjectId = SubjectId("idp-sub-001"),
        roles: Set<Role> = setOf(Role.REGISTRAR),
    ): Account = Account.create(subjectId, roles).unwrap()
}
