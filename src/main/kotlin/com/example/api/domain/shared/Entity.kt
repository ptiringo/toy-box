package com.example.api.domain.shared

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

    /**
     * 楽観ロックの version（永続化メタデータ）。null は「まだ永続化されていない」ことを表す。
     *
     * 永続化層（Spring Data JDBC）が insert / update の判別と競合検出に用いる。ドメインロジックは
     * この値で業務判断（分岐・比較）をしないこと。永続化される更新対象の集約だけが constructor プロパティで override し、それ以外は既定の null
     * のままでよい。業務判断の禁止は ArchUnit（`versionIsNotUsedForBusinessDecisions`）で機械強制している（集約自身の `copy`
     * による引き回しは自己参照として対象外）。
     */
    open val version: Long? = null

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
