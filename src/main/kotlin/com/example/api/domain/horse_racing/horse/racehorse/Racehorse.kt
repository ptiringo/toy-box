package com.example.api.domain.horse_racing.horse.racehorse

import com.example.api.domain.horse_racing.horse.bloodhorse.BloodHorseId
import org.jmolecules.ddd.annotation.Entity

/** 競走馬 */
@Entity
@Suppress("unused")
data class Racehorse(val bloodHorseId: BloodHorseId)
