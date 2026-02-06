package com.example.api.domain.horseracing.horse.racehorse

import com.example.api.domain.horseracing.horse.bloodhorse.BloodHorseId

/** 競走馬 */
@Suppress("unused")
data class Racehorse(
    val bloodHorseId: BloodHorseId,
)
