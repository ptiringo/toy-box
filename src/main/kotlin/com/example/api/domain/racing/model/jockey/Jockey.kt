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
 * 不変条件を満たした上で生成するために、コンストラクタは private にして [Jockey.create]（新規生成）または
 * [Jockey.reconstitute]（永続化層からの復元）でのみ生成する。
 *
 * @property id ジョッキーID
 * @property firstName 名
 * @property lastName 姓
 */
@AggregateRoot
class Jockey
private constructor(
    /** ジョッキーID */
    @field:Identity override val id: JockeyId,
    /** 名 */
    val firstName: String,
    /** 姓 */
    val lastName: String,
) : Entity<JockeyId>() {

    companion object {
        /**
         * 不変条件を検証してから [Jockey] を新規生成する。生成時に一意な ID を自動採番する。
         *
         * @return 生成された [Jockey]、または不変条件違反を表す [JockeyValidationError]
         */
        fun create(firstName: String, lastName: String): Result<Jockey, JockeyValidationError> =
            when {
                firstName.isBlank() -> Err(JockeyValidationError.BlankFirstName)
                lastName.isBlank() -> Err(JockeyValidationError.BlankLastName)
                else -> Ok(Jockey(JockeyId(generateId()), firstName, lastName))
            }

        /**
         * 永続化層に保存済みの状態から [Jockey] を再構成（リハイドレート）する。
         *
         * 既に [create] の検証を通過して保存された状態の復元であり、不変条件の再検証も ID の再採番も行わない。 永続化アダプター（infrastructure
         * 層）からの復元専用であり、新規生成には [create] を使うこと。
         */
        fun reconstitute(id: JockeyId, firstName: String, lastName: String): Jockey =
            Jockey(id, firstName, lastName)
    }
}
