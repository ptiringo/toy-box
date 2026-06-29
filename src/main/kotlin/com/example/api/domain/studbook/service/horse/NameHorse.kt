package com.example.api.domain.studbook.service.horse

import com.example.api.domain.shared.StateTransition
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseNamed
import com.example.api.domain.studbook.model.horse.bloodhorse.NameHorseError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError

/**
 * 馬名を登録するドメインサービス。
 *
 * 馬名登録の前提条件は性質が異なる2系統:
 * - **馬名の一意性（馬名登録原簿に既存の馬名は不可）** … 既存の馬名集合をまたぐ集合制約で、単一インスタンスの構築では 完結しない。本サービスが
 *   [bloodHorseRepository] から既存の使用を引き当てて検証する（[NameHorseError.NameAlreadyTaken]）。
 * - **二重命名の禁止** … 対象集約自身の不変条件であり、集約の [BloodHorse.assignName] が担う （[NameHorseError.AlreadyNamed]）。
 *
 * 一意性は永続化された馬名集合に対する問い合わせが本質であるため、本サービスがリポジトリポート [bloodHorseRepository] を 直接受け取って引き当てる（リポジトリポートは
 * domainModel に属するため、ドメインサービスからの依存はオニオンの 依存方向 service → model
 * に反しない。ADR-0022）。一意性を満たせば集約の状態遷移へ委譲する。照合順は集合制約を 先に判定し、通過後に [BloodHorse.assignName]
 * を呼ぶ（[recordCovering] と一貫）。
 *
 * 照合するのは **馬名登録原簿に既存の馬名との完全一致** のみ。保護馬名（GI 勝馬名・種牡馬名 等）の照合と、
 * 紛らわしい馬名の曖昧一致はマスタ整備・類似判定を要するため本サービスの対象外とし、別途継続検討する（#478）。
 *
 * @param bloodHorse 命名対象の軽種馬
 * @param horseName 付与する馬名
 * @param bloodHorseRepository 馬名の既存使用を引き当てる軽種馬ポート（読み取りのみ）
 * @return 命名済みの [BloodHorse] と [HorseNamed] を同梱した [StateTransition]、または [NameHorseError]
 */
fun nameHorse(
    bloodHorse: BloodHorse,
    horseName: HorseName,
    bloodHorseRepository: BloodHorseRepository,
): Result<StateTransition<BloodHorse, HorseNamed>, NameHorseError> {
    if (bloodHorseRepository.existsByName(horseName)) {
        return Err(NameHorseError.NameAlreadyTaken(horseName))
    }
    return bloodHorse.assignName(horseName).mapError { NameHorseError.AlreadyNamed(it.currentName) }
}
