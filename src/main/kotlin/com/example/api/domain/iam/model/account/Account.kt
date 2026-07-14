package com.example.api.domain.iam.model.account

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.Permission
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** IdP（GCP Identity Platform）が発行する ID トークンの `sub`。アカウントと IdP ユーザーの結び目。 */
@ValueObject @JvmInline value class SubjectId(val value: String)

/**
 * アカウントに与える役割。制度上の立場に沿わせる。
 * - [REGISTRAR]: 登録機関（JAIRS）の職員。全ての書き込みを行える。
 * - [BREEDER]: 生産者・種牡馬所有者。届出系（種付／未種付の記録・分娩報告・各種報告）のみ。
 * - [VIEWER]: 読み取りのみ。書き込み権限を持たない。
 */
enum class Role {
    REGISTRAR,
    BREEDER,
    VIEWER,
}

/** アカウント生成時の検証エラー。 */
sealed interface AccountValidationError {
    /** IdP の subject が空白。 */
    data object BlankSubjectId : AccountValidationError

    /** 役割が一つも与えられていない（権限ゼロのアカウントは作らせない）。 */
    data object NoRoles : AccountValidationError
}

/**
 * この API の利用者アカウント。IdP の `sub` に、自前で管理する役割を結びつける。
 *
 * 資格情報（パスワード等）は持たない。認証は IdP に委譲し、この集約が持つのは「誰が、どの役割か」 だけ（ADR-0064）。役割 →
 * 権限の展開はロール定義（マスタ）側の関心事なので、[toActor] は展開済みの 権限を受け取る。
 */
@AggregateRoot
class Account
private constructor(
    @field:Identity override val id: AccountId,
    val subjectId: SubjectId,
    val roles: Set<Role>,
    override val version: Long? = null,
) : Entity<AccountId>() {

    /** 役割から展開済みの [permissions] を与えて、ユースケースへ渡すパスポート [Actor] に写す。 */
    fun toActor(permissions: Set<Permission>): Actor = Actor(id, permissions)

    companion object {
        fun create(
            subjectId: SubjectId,
            roles: Set<Role>,
        ): Result<Account, AccountValidationError> =
            when {
                subjectId.value.isBlank() -> Err(AccountValidationError.BlankSubjectId)
                roles.isEmpty() -> Err(AccountValidationError.NoRoles)
                else -> Ok(Account(AccountId(generateId()), subjectId, roles))
            }

        fun reconstitute(
            id: AccountId,
            subjectId: SubjectId,
            roles: Set<Role>,
            version: Long?,
        ): Account = Account(id, subjectId, roles, version)
    }
}
