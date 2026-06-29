package com.example.api.domain.studbook.model.horse.bloodhorse

/**
 * 馬名登録（[nameHorse] ドメインサービス）の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface NameHorseError {
    /** 申請馬名が既に他の軽種馬で使用済み（馬名登録原簿の一意性違反）。 */
    data class NameAlreadyTaken(val name: HorseName) : NameHorseError

    /** 対象が既に命名済みで再命名できない（集約内不変条件 [HorseAlreadyNamed] 由来）。 */
    data class AlreadyNamed(val currentName: HorseName) : NameHorseError
}
