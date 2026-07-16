package com.example.api.domain.studbook.service.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumberAlreadyTaken
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * 血統登録番号の一意性を検証するドメインサービス。
 *
 * 血統登録番号が登録原簿の中で一意であること（登録規程 第3条＝血統登録は個体識別を明らかにする登録／
 * 第4条＝血統登録原簿に登録番号を記載）は、既存レコード集合への問い合わせがなければ判定できない 集合制約であり、単一集約の構築時不変条件では完結しない。よって本サービスがリポジトリポート
 * [repository] を直接受け取り既存の採番を引き当てる（読み取りのみ。ADR-0022）。血統登録番号と繁殖登録番号は
 * 別の採番空間のため、本サービスは血統登録原簿（[BloodHorseRepository]）のみを見る。
 *
 * @param number 検証する血統登録番号
 * @param repository 既存の採番を引き当てる軽種馬ポート（読み取りのみ）
 * @return 未使用なら [Ok]（[Unit]）、既に採番済みなら [PedigreeRegistrationNumberAlreadyTaken]
 */
fun ensurePedigreeRegistrationNumberAvailable(
    number: PedigreeRegistrationNumber,
    repository: BloodHorseRepository,
): Result<Unit, PedigreeRegistrationNumberAlreadyTaken> =
    if (repository.existsByRegistrationNumber(number)) {
        Err(PedigreeRegistrationNumberAlreadyTaken(number))
    } else {
        Ok(Unit)
    }
