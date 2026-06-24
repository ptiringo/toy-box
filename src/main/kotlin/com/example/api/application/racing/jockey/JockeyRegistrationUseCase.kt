package com.example.api.application.racing.jockey

import com.example.api.domain.racing.model.jockey.Jockey
import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.racing.model.jockey.JockeyRepository
import com.example.api.domain.racing.model.jockey.JockeyValidationError
import com.example.api.domain.shared.Command
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.mapError
import org.springframework.stereotype.Service

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
}

/**
 * ジョッキー登録ユースケース。
 *
 * 業務ルール:
 * - Jockey の不変条件を満たす（[Jockey.create] で検証）
 * - 同姓同名のジョッキーが既に存在してはならない
 *
 * Controller 層は本クラスのみに依存し、[JockeyRepository] 等のポートは知らない。
 *
 * @return 登録された [Jockey]、または業務ルール違反を表す [JockeyRegistrationError]
 */
@Service
class JockeyRegistrationUseCase(private val jockeyRepository: JockeyRepository) {
    operator fun invoke(
        command: Command<RegisterJockeyCommand>
    ): Result<Jockey, JockeyRegistrationError> {
        val input = command.payload
        return Jockey.create(input.firstName, input.lastName)
            .mapError { JockeyRegistrationError.InvalidJockey(it) }
            .andThen { jockey ->
                val duplicate = jockeyRepository.findByFullName(input.firstName, input.lastName)
                if (duplicate != null) {
                    Err(JockeyRegistrationError.DuplicateJockey(duplicate.id))
                } else {
                    Ok(jockeyRepository.save(jockey))
                }
            }
    }
}
