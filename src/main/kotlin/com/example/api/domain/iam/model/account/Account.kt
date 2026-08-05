package com.example.api.domain.iam.model.account

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/**
 * IdP（Identity Platform）が発行する ID トークンの `sub`。
 *
 * この API はパスワード等の資格情報を持たず、利用者の同一性はこの値だけで決まる（ADR-0064）。
 *
 * @property value IdP の subject 文字列
 */
@ValueObject @JvmInline value class SubjectId(val value: String)

/** IdP の subject がブランクだった。 */
data object BlankSubjectId

/**
 * この API の利用者アカウントを表す集約ルート。
 *
 * 役割は「IdP の subject と、この API が採番した [AccountId] を結びつける」ことだけ。ロールも権限も持たない
 * （認可はプレイヤーごとの世界によるテナント分離に一本化しており、権限を配る軸が存在しない）。
 *
 * 不変条件:
 * - `subjectId` がブランクではない
 *
 * @property id アカウントID
 * @property subjectId IdP の subject
 */
@AggregateRoot
class Account
private constructor(
    /** アカウントID */
    @field:Identity override val id: AccountId,
    /** IdP の subject */
    val subjectId: SubjectId,
    override val version: Long? = null,
) : Entity<AccountId>() {

    companion object {
        /**
         * 不変条件を検証してから [Account] を新規生成する。生成時に一意な ID を自動採番する。
         *
         * @return 生成された [Account]、または不変条件違反を表す [BlankSubjectId]
         */
        fun create(subjectId: String): Result<Account, BlankSubjectId> =
            if (subjectId.isBlank()) {
                Err(BlankSubjectId)
            } else {
                Ok(Account(AccountId(generateId()), SubjectId(subjectId)))
            }

        /**
         * 永続化層に保存済みの状態から [Account] を再構成（リハイドレート）する。
         *
         * 不変条件の再検証も ID の再採番も行わない。infrastructure 層からの復元専用。
         */
        fun reconstitute(id: AccountId, subjectId: SubjectId, version: Long?): Account =
            Account(id, subjectId, version)
    }
}
