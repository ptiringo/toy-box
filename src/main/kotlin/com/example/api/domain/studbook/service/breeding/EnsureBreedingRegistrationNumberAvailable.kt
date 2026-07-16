package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationNumberAlreadyTaken
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * 繁殖登録番号の一意性を検証するドメインサービス。
 *
 * 繁殖登録番号が繁殖登録原簿の中で一意であること（登録規程 第5条）は、既存レコード集合への問い合わせが なければ判定できない集合制約。よって本サービスがリポジトリポート [repository]
 * を直接受け取り既存の採番を 引き当てる（読み取りのみ。ADR-0022）。血統登録番号とは別の採番空間のため、本サービスは繁殖登録原簿
 * （[BreedingRegistrationRepository]）のみを見る。
 *
 * @param number 検証する繁殖登録番号
 * @param repository 既存の採番を引き当てる繁殖登録ポート（読み取りのみ）
 * @return 未使用なら [Ok]（[Unit]）、既に採番済みなら [BreedingRegistrationNumberAlreadyTaken]
 */
fun ensureBreedingRegistrationNumberAvailable(
    number: BreedingRegistrationNumber,
    repository: BreedingRegistrationRepository,
): Result<Unit, BreedingRegistrationNumberAlreadyTaken> =
    if (repository.existsByRegistrationNumber(number)) {
        Err(BreedingRegistrationNumberAlreadyTaken(number))
    } else {
        Ok(Unit)
    }
