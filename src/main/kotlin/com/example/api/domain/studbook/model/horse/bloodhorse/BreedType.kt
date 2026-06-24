package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 品種。
 *
 * 血統登録で記録される軽種馬の品種。区分は JAIRS「登録規程実施基準」別表「登録する軽種馬の品種の定め方」の 5 区分に対応する（#277）。
 * 別表では品種は父・母の登録品種の組合せから決定論的に導出され、その閾値の鍵は **アラブ血量(%)** である。
 *
 * 本モデルでは品種整合の検証（[isConsistentWith]）を **父母の品種型だけで一意に決まる純粋種（[THOROUGHBRED] / [ARAB]）に限定**する。
 * アラブ血量の閾値で定義される [ANGLO_ARAB] / [THOROUGHBRED_TYPE] / [ARAB_TYPE] は、父母の「品種型」だけからは一意に判定できない
 * （アラブ血量は父母の血量の平均として世代をまたいで伝播する量であり、品種型に還元できない）。これらは血量モデルを導入するまで
 * 整合検証の対象外（常に整合とみなす）とし、必要になった時点で拡張する。経緯と典拠は #277 を参照。
 */
@ValueObject
enum class BreedType {
    /** サラブレッド（父母ともサラブレッド血統書登録、またはサラブレッド系種にサラブレッドを連続 8 代以上交配し承認されたもの） */
    THOROUGHBRED,

    /** アラブ（純血のアラブ） */
    ARAB,

    /** アングロアラブ（アラブ血量 25% 以上） */
    ANGLO_ARAB,

    /** サラブレッド系種（アラブ血量 5% 未満） */
    THOROUGHBRED_TYPE,

    /** アラブ系種（アラブ血量 25% 以上） */
    ARAB_TYPE;

    /**
     * この品種を仔の品種としたとき、父 [sire]・母 [dam] の品種と整合するか判定する。
     *
     * 父母の品種型だけで一意に決まる純粋種のみ検証する:
     * - [THOROUGHBRED]（サラブレッド）は両親ともサラブレッドである場合のみ整合する。
     * - [ARAB]（アラブ）は両親ともアラブである場合のみ整合する。
     *
     * アラブ血量の閾値で定義される [ANGLO_ARAB] / [THOROUGHBRED_TYPE] / [ARAB_TYPE] は、父母の品種型だけからは整合を判定できない
     * ため本モデルでは整合検証の対象外とし、常に `true` を返す（クラス KDoc 参照）。
     */
    fun isConsistentWith(sire: BreedType, dam: BreedType): Boolean =
        when (this) {
            THOROUGHBRED -> sire == THOROUGHBRED && dam == THOROUGHBRED
            ARAB -> sire == ARAB && dam == ARAB
            ANGLO_ARAB,
            THOROUGHBRED_TYPE,
            ARAB_TYPE -> true
        }
}
