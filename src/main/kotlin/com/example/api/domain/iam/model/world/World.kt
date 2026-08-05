package com.example.api.domain.iam.model.world

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 世界の名前。プレイヤーがセーブデータを見分けるためのラベル。 */
@ValueObject @JvmInline value class WorldName(val value: String)

/** 世界の名前の不変条件違反。 */
sealed interface WorldNameValidationError {
    /** 名前がブランク。 */
    data object Blank : WorldNameValidationError

    /** 名前が上限（64 文字）を超えている。 DB の `iam.world.name VARCHAR(64)` と揃える。 */
    data object TooLong : WorldNameValidationError
}

/**
 * プレイヤーごとの世界（セーブデータ）を表す集約ルート。
 *
 * 全ドメインのデータはいずれかの世界に属し、世界をまたいでデータが見えることはない。この API の唯一の 認可判断「この世界はあなたのものか」は [isOwnedBy] が答える。
 *
 * **世界はドメインを知らない。** 種別（軽種馬 / 坂道 / テニス）を属性に持たせると `iam` が全コンテキストの
 * 語彙を知ることになるため持たせない。使い分けたいプレイヤーは世界を複数作って名前で分ける。
 *
 * 不変条件:
 * - `name` がブランクでなく、64 文字以下
 *
 * 集約はイミュータブル（ADR-0009）。[rename] は自身を書き換えず、新しい [World] を返す。
 *
 * @property id 世界ID
 * @property accountId 世界を所有するアカウントのID
 * @property name 世界の名前
 */
@AggregateRoot
class World
private constructor(
    /** 世界ID */
    @field:Identity override val id: WorldId,
    /** 所有者のアカウントID */
    val accountId: AccountId,
    /** 世界の名前 */
    val name: WorldName,
    override val version: Long? = null,
) : Entity<WorldId>() {

    /**
     * 世界の名前を変える。
     *
     * @return 新しい名前を持つ [World]、または不変条件違反を表す [WorldNameValidationError]
     */
    fun rename(name: String): Result<World, WorldNameValidationError> =
        validateName(name).map { World(id, accountId, it, version) }

    /** 指定のアカウントがこの世界の所有者かを判定する。 */
    fun isOwnedBy(accountId: AccountId): Boolean = this.accountId == accountId

    companion object {
        /** 世界の名前の上限。DB の `iam.world.name VARCHAR(64)` と揃える。 */
        private const val MAX_NAME_LENGTH = 64

        /**
         * 不変条件を検証してから [World] を新規生成する。生成時に一意な ID を自動採番する。
         *
         * @return 生成された [World]、または不変条件違反を表す [WorldNameValidationError]
         */
        fun create(accountId: AccountId, name: String): Result<World, WorldNameValidationError> =
            validateName(name).map { World(WorldId(generateId()), accountId, it) }

        /**
         * 永続化層に保存済みの状態から [World] を再構成（リハイドレート）する。
         *
         * 不変条件の再検証も ID の再採番も行わない。infrastructure 層からの復元専用。
         */
        fun reconstitute(
            id: WorldId,
            accountId: AccountId,
            name: WorldName,
            version: Long?,
        ): World = World(id, accountId, name, version)

        private fun validateName(name: String): Result<WorldName, WorldNameValidationError> =
            when {
                name.isBlank() -> Err(WorldNameValidationError.Blank)
                name.length > MAX_NAME_LENGTH -> Err(WorldNameValidationError.TooLong)
                else -> Ok(WorldName(name))
            }
    }
}
