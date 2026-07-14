package com.example.api.domain.iam.model.account

import com.example.api.domain.shared.Permission
import org.jmolecules.ddd.annotation.Repository

/** [Account] の永続化ポート。 */
@Repository
interface AccountRepository {
    /** IdP の subject からアカウントを引き当てる。未登録なら null。 */
    fun findBySubjectId(subjectId: SubjectId): Account?

    /** 役割の集合に紐づく権限（マスタ定義）を展開する。 */
    fun findPermissionsOf(roles: Set<Role>): Set<Permission>
}
