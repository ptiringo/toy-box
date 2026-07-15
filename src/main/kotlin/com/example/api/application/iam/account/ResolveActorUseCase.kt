package com.example.api.application.iam.account

import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.Actor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toResultOr
import org.springframework.stereotype.Service

/**
 * 認証済みリクエストの subject（IdP の `sub`）から [Actor] を引き当てるクエリの入力。
 *
 * 読み取り系の入力は素の DTO とし、書き込み系の [com.example.api.domain.shared.Command] 封筒
 * （発生時刻メタデータ）は使わない。発生時刻は書き込みイベントの概念であり、読み取りには不要（ADR-0031）。
 */
data class ResolveActorQuery(val subjectId: String)

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

    operator fun invoke(query: ResolveActorQuery): Result<Actor, ResolveActorUseCaseError> {
        val subjectId = query.subjectId
        return accountRepository
            .findBySubjectId(SubjectId(subjectId))
            .toResultOr { ResolveActorUseCaseError.AccountNotFound(subjectId) }
            .map { account -> account.toActor(accountRepository.findPermissionsOf(account.roles)) }
    }
}
