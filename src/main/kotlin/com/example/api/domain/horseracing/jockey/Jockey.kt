package com.example.api.domain.horseracing.jockey

import com.fasterxml.uuid.Generators
import java.util.UUID

/**
 * ジョッキーIDを表す値クラス
 *
 * @property value UUID形式のID値
 */
@JvmInline
value class JockeyId(
    val value: UUID,
)

/**
 * 騎手（ジョッキー）を表すデータクラス
 *
 * @property firstName 名
 * @property lastName 姓
 * @property id ジョッキーID（自動生成）
 */
class Jockey(
    /** 名 */
    val firstName: String,
    /** 姓 */
    val lastName: String,
) {
    /**
     * ジョッキーID
     * インスタンス生成時に一意なIDを自動生成する
     */
    val id = JockeyId(Generators.timeBasedEpochRandomGenerator().generate())

    /**
     * 等価判定
     * 同じ型かつIDが一致する場合のみ等価とみなす
     *
     * @param other 比較対象
     * @return 等価ならtrue
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as Jockey).id
    }

    /**
     * ハッシュコード生成
     * ジョッキーIDに基づいてハッシュ値を返す
     *
     * @return ハッシュ値
     */
    override fun hashCode(): Int = id.hashCode()
}
