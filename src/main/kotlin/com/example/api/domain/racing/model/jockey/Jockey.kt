package com.example.api.domain.racing.model.jockey

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/**
 * ジョッキーIDを表す値クラス
 *
 * @property value UUID形式のID値
 */
@ValueObject @JvmInline value class JockeyId(val value: UUID)

/** ジョッキー生成時に発生しうる不変条件違反。 */
sealed interface JockeyValidationError {
    /** 名がブランク。 */
    data object BlankFirstName : JockeyValidationError

    /** 姓がブランク。 */
    data object BlankLastName : JockeyValidationError
}

/**
 * 騎手（ジョッキー）を表すエンティティ。
 *
 * 不変条件:
 * - `firstName` / `lastName` のいずれもブランクではない
 *
 * 不変条件を満たした上で生成するために、コンストラクタは private にして [Jockey.create] でのみ生成する。
 *
 * @property firstName 名
 * @property lastName 姓
 * @property id ジョッキーID（自動生成）
 */
@AggregateRoot
class Jockey
private constructor(
    /** 名 */
    val firstName: String,
    /** 姓 */
    val lastName: String,
) : Entity<JockeyId>() {
    /** ジョッキーID インスタンス生成時に一意なIDを自動生成する */
    @field:Identity override val id = JockeyId(generateId())

    companion object {
        /**
         * 不変条件を検証してから [Jockey] を生成する。
         *
         * @return 生成された [Jockey]、または不変条件違反を表す [JockeyValidationError]
         */
        fun create(firstName: String, lastName: String): Result<Jockey, JockeyValidationError> =
            when {
                firstName.isBlank() -> Err(JockeyValidationError.BlankFirstName)
                lastName.isBlank() -> Err(JockeyValidationError.BlankLastName)
                else -> Ok(Jockey(firstName, lastName))
            }
    }
}
