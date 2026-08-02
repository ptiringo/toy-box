package com.example.api.domain.tennis.model.player

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 生年月日。
 *
 * 時刻・タイムゾーンを持たない暦日として [LocalDate] で保持する。未来日でないこと等の検証は、判定に基準時刻（クロック）を
 * 要するため値オブジェクト単体では行わず、必要に応じて上位（ドメインサービス／アプリケーション層）でコマンドの発生時刻と 突き合わせて検証する。
 *
 * @property value 出生した暦日
 */
@ValueObject @JvmInline value class DateOfBirth(val value: LocalDate)
