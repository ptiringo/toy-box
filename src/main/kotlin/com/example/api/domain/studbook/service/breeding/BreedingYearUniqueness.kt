package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import java.time.Year

/**
 * 「繁殖牝馬 × 繁殖年」で一意という集合制約のために、同じ繁殖牝馬・同じ繁殖年の既存の繁殖成績を引き当てる。
 *
 * 年次成績は国内種付・海外種付・種付せずのいずれでも 1 年に 1 つ（様式第14号が報告する年次成績は種付年ごとに1行）。 記録系のドメインサービス（recordCovering /
 * recordOverseasCovering / recordUncovered）が共通に使う（ADR-0022）。
 *
 * @return 既存の繁殖成績。無ければ null
 */
fun findExistingForYear(
    worldId: WorldId,
    broodmareRegistration: BreedingRegistration,
    breedingYear: Year,
    repository: BreedingResultRepository,
): BreedingResult? =
    repository.findByBreedingRegistrationIdAndBreedingYear(
        worldId,
        broodmareRegistration.id,
        breedingYear,
    )
