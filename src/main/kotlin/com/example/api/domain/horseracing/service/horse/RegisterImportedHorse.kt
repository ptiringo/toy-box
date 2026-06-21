package com.example.api.domain.horseracing.service.horse

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.ImportedHorseEntry
import com.example.api.domain.horseracing.model.horse.bloodhorse.PedigreeRegistrationNumber

/**
 * 父母不明の輸入馬・基礎輸入馬の血統登録を行い、軽種馬（[BloodHorse]）を誕生させる。
 *
 * 内国産馬の registerInStudBook と異なり、父・母が当システムに存在しないため父母の引き当て・親子判定・親仔の品種整合は行わない。
 * 品種・血統は承認海外機関の血統書及び輸出証明書に依拠する前提とし（その審査自体は別途のモデリングに委ねる）、本サービスは輸入馬固有の 個体識別情報（原産国・揚陸日を含む
 * [ImportedHorseEntry]）から [BloodHorse] を生成する。
 *
 * 現状は内国産馬のような前提条件違反を持たないため [BloodHorse] をそのまま返す。生成口 [BloodHorse.ofImported] は internal で
 * あり、この生成を本サービスに封じ込めることで「血統登録を経た個体」であることを型で担保する（内国産馬の registerInStudBook と同じ方針）。
 *
 * @param entry 輸入馬自身の個体識別情報（原産国・揚陸日を含む）
 * @param registrationNumber 交付される血統登録番号
 * @return 生成された [BloodHorse]
 */
fun registerImportedHorse(
    entry: ImportedHorseEntry,
    registrationNumber: PedigreeRegistrationNumber,
): BloodHorse = BloodHorse.ofImported(entry, registrationNumber)
