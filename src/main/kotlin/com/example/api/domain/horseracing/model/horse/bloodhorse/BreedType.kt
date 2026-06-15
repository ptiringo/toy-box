package com.example.api.domain.horseracing.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 品種。
 *
 * 血統登録で記録される軽種馬の品種。血統登録時には親仔の品種が整合している必要がある（[isConsistentWith]）。
 *
 * 本モデルでは品種整合の検証対象を「サラブレッド種」に絞る（サラブレッド種の仔は両親ともサラブレッド種であることを要する）。
 * アングロアラブ種等の交配規則は制度上より複雑なため、現時点では整合検証の対象外（常に整合とみなす）とし、規則が必要になった時点で拡張する。
 */
@ValueObject
enum class BreedType {
    /** サラブレッド種 */
    THOROUGHBRED,

    /** アングロアラブ種 */
    ANGLO_ARAB,

    /** アラブ種 */
    ARAB,

    /** 準サラブレッド種 */
    HALF_BRED;

    /**
     * この品種を仔の品種としたとき、父 [sire]・母 [dam] の品種と整合するか判定する。
     *
     * サラブレッド種は両親ともサラブレッド種である場合のみ整合する。それ以外の品種は本モデルでは整合検証の対象外とし、常に `true` を返す。
     */
    fun isConsistentWith(sire: BreedType, dam: BreedType): Boolean =
        when (this) {
            THOROUGHBRED -> sire == THOROUGHBRED && dam == THOROUGHBRED
            ANGLO_ARAB,
            ARAB,
            HALF_BRED -> true
        }
}
