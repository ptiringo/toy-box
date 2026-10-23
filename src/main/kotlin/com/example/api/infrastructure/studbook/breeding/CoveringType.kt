package com.example.api.infrastructure.studbook.breeding

/**
 * `studbook.breeding_result.covering_type` に書かれる判別子の文字列。
 *
 * sealed な種付 [com.example.api.domain.studbook.model.breeding.Covering] をフラット化した判別子列の値で、
 * 書き込み側（[JdbcBreedingResultRepository]）と読み取り側（[JdbcBreedingResultQueries]）の双方が同じ語彙を使う。 出所をこの 1
 * 箇所に集約する理由は [com.example.api.infrastructure.studbook.horse.OriginType] と同じ。値は `Covering` の
 * 各バリアント名に対応し、CHECK 制約 `chk_breeding_result_covering`（V25 で VALIDATE 済み）がスキーマ側でも許容値を縛る。
 * 種付せずの年は判別子を持たない（NULL）。
 */
internal object CoveringType {
    /** 国内種付（日本で繁殖登録された種牡馬との種付）。 */
    const val DOMESTIC = "DOMESTIC"

    /** 海外種付（外国種牡馬との外国での種付）。 */
    const val OVERSEAS = "OVERSEAS"
}
