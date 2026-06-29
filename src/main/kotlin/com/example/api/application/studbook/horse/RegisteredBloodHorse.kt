package com.example.api.application.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.inspection.HorseInspection

/**
 * 血統登録の結果として、軽種馬とその個体識別・親子判定審査を束ねた application 層の戻り値。
 *
 * 識別子（マイクロチップ）は審査（[HorseInspection]）側が保持するため、軽種馬リソース表現（マイクロチップを露出する
 * `BloodHorseResponse`）の組み立てには審査が要る。controller へ両者を渡すための束。審査を一級リソースとして扱う 対外 API は別途（フォロー issue）。
 *
 * @property bloodHorse 登録された軽種馬
 * @property inspection その個体識別・親子判定審査
 */
data class RegisteredBloodHorse(val bloodHorse: BloodHorse, val inspection: HorseInspection)
