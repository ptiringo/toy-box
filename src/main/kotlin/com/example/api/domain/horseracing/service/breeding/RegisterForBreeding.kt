package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistration
import com.example.api.domain.horseracing.model.breeding.BreedingRegistrationNumber
import com.example.api.domain.horseracing.model.breeding.BreedingRole
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse

/**
 * 血統登録済みの馬を繁殖の用に供するため繁殖登録する。
 *
 * 繁殖登録（JAIRS）は雄雌共通の単一の登録で、性によって付与される [BreedingRole]（雄=種牡馬／雌=繁殖牝馬）が 決まる。本サービスは性からロールを定めて
 * [BreedingRegistration] を生成する。
 *
 * 制度上の前提条件（①血統登録済み ②馬名登録済み ③競走馬登録があれば抹消済み）のうち、①血統登録済みは [BloodHorse] の参照自体で
 * 担保される。②馬名登録・③競走馬登録抹消は対応する集約が未モデル化のため現時点では検証しない。検証すべき前提条件が 増えたら戻り値を `Result` に変える。
 *
 * @param horse 繁殖登録する馬（血統登録済みの [BloodHorse]）
 * @param registrationNumber 交付される繁殖登録番号
 * @return 生成された [BreedingRegistration]
 */
fun registerForBreeding(
    horse: BloodHorse,
    registrationNumber: BreedingRegistrationNumber,
): BreedingRegistration =
    BreedingRegistration.of(registrationNumber, horse.id, BreedingRole.from(horse.sex))
