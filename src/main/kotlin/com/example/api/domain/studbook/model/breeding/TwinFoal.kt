package com.example.api.domain.studbook.model.breeding

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 双子として生産された産駒のもう一頭。
 *
 * 繁殖成績報告書（様式第14号）は、表の「産駒あり」欄に「双子」の印を持ち、もう一頭の成績を裏の「双子の繁殖成績」欄に書かせる（登録規程実施基準p.39–40）。
 * 表と裏で生年月日の欄が分かれているため、もう一頭の分娩日を別に持つ。
 *
 * @property foalingDate もう一頭の分娩日（産駒の出生年月日）
 */
@ValueObject data class TwinFoal(val foalingDate: LocalDate)
