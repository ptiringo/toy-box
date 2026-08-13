package com.example.api.infrastructure.studbook.horse

/**
 * `studbook.blood_horse.origin_type` に書かれる判別子の文字列。
 *
 * sealed な出自 [com.example.api.domain.studbook.model.horse.bloodhorse.Origin] をフラット化した判別子列の値で、
 * 書き込み側（[JdbcBloodHorseRepository] の集約 ⇔ Row マッピング）と読み取り側（[JdbcBloodHorseQueries] の View
 * 直組み）の双方が同じ語彙を使う。どちらか一方が生リテラルを持つと、判別子のリネームがもう一方を無言で 壊すため、出所をこの 1 箇所に集約する。値は `Origin`
 * の各バリアント名に対応し、CHECK 制約 `chk_blood_horse_origin`（V16 で VALIDATE 済み）がスキーマ側でも許容値を縛っている。
 */
internal object OriginType {
    /** 内国産（父母とも当システムに血統登録済み）。 */
    const val DOMESTIC = "DOMESTIC"

    /** 輸入馬・基礎輸入馬（父母不明。原産国・揚陸日を持つ）。 */
    const val IMPORTED = "IMPORTED"

    /** 移行取り込み（先行する登録原簿からの取り込み。バリアント固有列は持たない）。 */
    const val CARRIED_OVER = "CARRIED_OVER"
}
