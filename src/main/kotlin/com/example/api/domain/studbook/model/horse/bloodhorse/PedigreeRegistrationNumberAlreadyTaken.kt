package com.example.api.domain.studbook.model.horse.bloodhorse

/**
 * 血統登録番号が既に他の軽種馬に採番済み（血統登録原簿の一意性違反）。
 *
 * 血統登録番号は登録原簿の中で一意（登録規程 第3条・第4条）。ドメインサービス
 * [com.example.api.domain.studbook.service.horse.ensurePedigreeRegistrationNumberAvailable] が
 * 既存レコード集合を引き当てて検証し、衝突時にこれを返す。
 */
data class PedigreeRegistrationNumberAlreadyTaken(val number: PedigreeRegistrationNumber)
