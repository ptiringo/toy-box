package com.example.api.domain

/**
 * ID ベースの等価性を持つエンティティの抽象基底クラス。
 *
 * DDD の「ID による等価性」原則に基づき、`equals` / `hashCode` を ID のみで実装する。 各エンティティはこのクラスを継承し、[id]
 * をオーバーライドするだけで一貫した等価性を持つ。
 *
 * @param ID エンティティ ID の型（`@JvmInline value class` を想定）
 */
abstract class Entity<ID : Any> {
    /** エンティティ ID */
    abstract val id: ID

    /** 等価判定 同じ型かつ ID が一致する場合のみ等価とみなす */
    final override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return id == (other as Entity<*>).id
    }

    /** ハッシュコード生成 ID に基づいてハッシュ値を返す */
    final override fun hashCode(): Int = id.hashCode()
}
