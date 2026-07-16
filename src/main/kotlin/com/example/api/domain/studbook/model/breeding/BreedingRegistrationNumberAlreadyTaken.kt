package com.example.api.domain.studbook.model.breeding

/**
 * 繁殖登録番号が既に他の繁殖登録に採番済み（繁殖登録原簿の一意性違反）。
 *
 * 繁殖登録番号は登録原簿の中で一意（登録規程 第5条）。血統登録番号とは別の採番空間。ドメインサービス
 * [com.example.api.domain.studbook.service.breeding.ensureBreedingRegistrationNumberAvailable] が
 * 既存レコード集合を引き当てて検証し、衝突時にこれを返す。
 */
data class BreedingRegistrationNumberAlreadyTaken(val number: BreedingRegistrationNumber)
