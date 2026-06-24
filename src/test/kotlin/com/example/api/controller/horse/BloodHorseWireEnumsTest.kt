package com.example.api.controller.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.DnaParentageResult
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import org.junit.jupiter.api.Test

/**
 * HTTP 契約 enum とドメイン enum の相互変換テスト。
 *
 * `〜Dto` とドメイン enum は現状どちらも同じ列挙子名を持つため、対応は「名前一致」で検証する。全列挙子を走査することで マッピングの `when`
 * 全分岐を網羅し、片方に列挙子が増えてマッピング漏れが生じればコンパイルエラーで検知される。
 */
class BloodHorseWireEnumsTest {
    @Test
    fun `性が名前一致で相互変換されること`() {
        SexDto.entries.forEach { assert(it.toDomain().name == it.name) }
        Sex.entries.forEach { assert(it.toApi().name == it.name) }
    }

    @Test
    fun `毛色が名前一致で相互変換されること`() {
        CoatColorDto.entries.forEach { assert(it.toDomain().name == it.name) }
        CoatColor.entries.forEach { assert(it.toApi().name == it.name) }
    }

    @Test
    fun `品種が名前一致で相互変換されること`() {
        BreedTypeDto.entries.forEach { assert(it.toDomain().name == it.name) }
        BreedType.entries.forEach { assert(it.toApi().name == it.name) }
    }

    @Test
    fun `DNA 判定結果がドメインへ名前一致で変換されること`() {
        DnaParentageResultDto.entries.forEach { assert(it.toDomain().name == it.name) }
    }

    @Test
    fun `各 Dto enum がドメイン enum と同じ列挙子集合を持つこと`() {
        assert(names<SexDto>() == names<Sex>())
        assert(names<CoatColorDto>() == names<CoatColor>())
        assert(names<BreedTypeDto>() == names<BreedType>())
        assert(names<DnaParentageResultDto>() == names<DnaParentageResult>())
    }

    private inline fun <reified E : Enum<E>> names(): Set<String> =
        enumValues<E>().map { it.name }.toSet()
}
