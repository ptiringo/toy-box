package com.example.api.domain.horseracing.horse.stallion

import com.example.api.domain.horseracing.horse.bloodhorse.BloodHorseId

/** 種牡馬 */
@Suppress("unused")
data class Stallion(
    val bloodHorseId: BloodHorseId,
)
