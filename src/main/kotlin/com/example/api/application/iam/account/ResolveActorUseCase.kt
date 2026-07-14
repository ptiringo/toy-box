package com.example.api.application.iam.account

import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toResultOr
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 認証済みリクエストの subject（IdP の `sub`）から [Actor] を引き当てる入力。 */
data class ResolveActorCommand(val subjectId: String)

/** [Actor] の引き当てに失敗する理由。 */
sealed interface ResolveActorUseCaseError {
    /**
     * IdP は認証したが、この API に対応する `account` 行が無い。
     *
     * 認証は通っているので 401 ではなく 403（何も許可されていない利用者）として扱う。IdP 側に ユーザーができても `account`
     * 行は自動では生えない（プロビジョニング経路は #606 の未解決事項）。
     */
    data class AccountNotFound(val subjectId: String) : ResolveActorUseCaseError
}

/**
 * subject → アカウント → 役割 → 権限を展開し、ユースケースへ渡すパスポート [Actor] を組む。
 *
 * リクエストごとに DB から組み立て、キャッシュしない（権限剥奪の即時反映を、単純さと引き換えに 優先する。#606 / ADR-0064）。
 */
@Service
class ResolveActorUseCase(private val accountRepository: AccountRepository) {

    @Transactional(readOnly = true)
    operator fun invoke(
        command: Command<ResolveActorCommand>
    ): Result<Actor, ResolveActorUseCaseError> {
        val subjectId = command.payload.subjectId
        return accountRepository
            .findBySubjectId(SubjectId(subjectId))
            .toResultOr { ResolveActorUseCaseError.AccountNotFound(subjectId) }
            .map { account -> account.toActor(accountRepository.findPermissionsOf(account.roles)) }
    }
}
