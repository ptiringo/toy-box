package com.example.api.application.racing.jockey

import com.example.api.application.shared.idempotency.IdempotencyStore
import com.example.api.domain.racing.model.jockey.Jockey
import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.racing.model.jockey.JockeyRepository
import com.example.api.domain.racing.model.jockey.JockeyValidationError
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.Idempotency
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onOk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ジョッキー登録ユースケースの入力コマンド。
 *
 * @property firstName 名
 * @property lastName 姓
 */
data class RegisterJockeyCommand(val firstName: String, val lastName: String)

/** ジョッキー登録時に発生しうる業務ルール違反。 */
sealed interface JockeyRegistrationError {
    /**
     * Jockey 不変条件違反を application 層エラーに wrap したもの。
     *
     * 個別バリアントは [JockeyValidationError] を参照する。
     */
    data class InvalidJockey(val cause: JockeyValidationError) : JockeyRegistrationError

    /** 同姓同名のジョッキーが既に登録済み。 */
    data class DuplicateJockey(val existingId: JockeyId) : JockeyRegistrationError

    /** 同じ冪等キーが、別の内容のリクエストで再利用された（ADR-0072）。 */
    data object IdempotencyKeyReused : JockeyRegistrationError
}

/**
 * ジョッキー登録ユースケース。
 *
 * 業務ルール:
 * - Jockey の不変条件を満たす（[Jockey.create] で検証）
 * - 同姓同名のジョッキーが既に存在してはならない
 *
 * `command.idempotency` が付いていれば、実処理の前に冪等キーの行を確保して再送を判定する（ADR-0072）。
 * 記録と業務書き込みは同じトランザクションに乗るため、「キーはあるがジョッキーが居ない」状態は生じない。
 *
 * Controller 層は本クラスのみに依存し、[JockeyRepository] 等のポートは知らない。
 *
 * @return 登録された [Jockey]、または業務ルール違反を表す [JockeyRegistrationError]
 */
@Service
class JockeyRegistrationUseCase(
    private val jockeyRepository: JockeyRepository,
    private val idempotencyStore: IdempotencyStore,
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RegisterJockeyCommand>,
    ): Result<Jockey, JockeyRegistrationError> {
        val idempotency = command.idempotency
        return if (idempotency == null) {
            register(actor, command.payload)
        } else {
            registerIdempotently(actor, command.payload, idempotency)
        }
    }

    /** 冪等キー付きの登録。行を確保して以降を直列化する。ここから先はこのトランザクションだけが同じキーを進められる。 */
    private fun registerIdempotently(
        actor: Actor,
        input: RegisterJockeyCommand,
        idempotency: Idempotency,
    ): Result<Jockey, JockeyRegistrationError> {
        val record =
            idempotencyStore.claim(actor.worldId, idempotency.key, idempotency.requestFingerprint)
        if (record.requestFingerprint != idempotency.requestFingerprint) {
            return Err(JockeyRegistrationError.IdempotencyKeyReused)
        }

        val replayed =
            record.resourceId?.let { jockeyRepository.findById(actor.worldId, JockeyId(it)) }
        return replayed?.let { Ok(it) }
            ?: register(actor, input).onOk {
                idempotencyStore.recordResource(actor.worldId, idempotency.key, it.id.value)
            }
    }

    private fun register(
        actor: Actor,
        input: RegisterJockeyCommand,
    ): Result<Jockey, JockeyRegistrationError> =
        Jockey.create(input.firstName, input.lastName)
            .mapError { JockeyRegistrationError.InvalidJockey(it) }
            .andThen { jockey ->
                val duplicate =
                    jockeyRepository.findByFullName(actor.worldId, input.firstName, input.lastName)
                if (duplicate != null) {
                    Err(JockeyRegistrationError.DuplicateJockey(duplicate.id))
                } else {
                    Ok(jockeyRepository.save(actor.worldId, jockey))
                }
            }
}
