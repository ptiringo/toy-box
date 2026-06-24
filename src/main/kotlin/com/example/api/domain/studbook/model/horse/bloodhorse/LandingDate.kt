package com.example.api.domain.studbook.model.horse.bloodhorse

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 揚陸日。
 *
 * 輸入馬・基礎輸入馬が本邦に陸揚げされた日。輸入馬の血統登録は出生・繁殖登録のチェーンではなく、この揚陸日を起算点とする
 * （登録の提出期限・登録料区分が揚陸日からの経過日数で定まる）。内国産馬は揚陸日を持たない（[BloodHorse.landingDate] は null）。
 *
 * 時刻・タイムゾーンを持たない暦日として [LocalDate] で保持する。揚陸日からの提出期限（90 日）の判定は基準時刻を要するため
 * ドメインモデル単体では行わず、必要に応じて上位で検証する。
 *
 * @property value 陸揚げされた暦日
 */
@ValueObject @JvmInline value class LandingDate(val value: LocalDate)
