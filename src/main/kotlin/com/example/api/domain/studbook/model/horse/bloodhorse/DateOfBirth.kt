package com.example.api.domain.studbook.model.horse.bloodhorse

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 生年月日。
 *
 * 軽種馬が出生した日。個体識別および年齢計算（競走馬の出走資格等）の基礎となる。
 *
 * 時刻・タイムゾーンを持たない暦日として [LocalDate] で保持する。未来日でないこと等の検証は、判定に基準時刻（クロック）を要するためドメインモデル単体では行わず、
 * 必要に応じて上位（ドメインサービス／アプリケーション層）でコマンドの発生時刻と突き合わせて検証する。
 *
 * @property value 出生した暦日
 */
@ValueObject @JvmInline value class DateOfBirth(val value: LocalDate)
