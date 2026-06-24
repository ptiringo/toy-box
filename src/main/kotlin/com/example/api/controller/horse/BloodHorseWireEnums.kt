package com.example.api.controller.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.DnaParentageResult
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex

/*
 * 軽種馬リソースの HTTP 契約で用いる enum 群と、ドメイン enum との相互変換。
 *
 * ドメイン enum（[Sex] 等）をそのまま wire に晒すと、ドメイン側の列挙子リネームが HTTP 契約（および生成クライアント）を
 * 無言で破壊する。これを避けるため adapter 層に契約専用の `〜Dto` enum を置き、`toDomain` / `toApi` の明示マッピングで
 * domain と往復させる。マッピングは網羅 when で書くため、ドメイン enum に列挙子が増減すると compile エラーで検知できる。
 *
 * 列挙子名は現状ドメインと同一だが、それは「現時点で wire 名とドメイン名が一致している」だけであり、両者の独立性は
 * このマッピング層で担保する。ドメイン enum をリネームしても、本ファイルの when 節を直せば wire 契約は不変に保てる。
 *
 * `toApi` は成功レスポンス [BloodHorseResponse] が露出する項目（性・毛色・品種）のみ用意する。DNA 判定は
 * リクエスト専用項目のためレスポンス変換を持たない。
 */

/** 性（HTTP 契約）。 */
enum class SexDto {
    /** 雄 */
    MALE,

    /** 雌 */
    FEMALE,
}

/** 毛色（HTTP 契約）。 */
enum class CoatColorDto {
    /** 栗毛 */
    CHESTNUT,

    /** 栃栗毛 */
    DARK_CHESTNUT,

    /** 鹿毛 */
    BAY,

    /** 黒鹿毛 */
    DARK_BAY,

    /** 青鹿毛 */
    BROWN,

    /** 青毛 */
    BLACK,

    /** 芦毛 */
    GRAY,

    /** 白毛 */
    WHITE,
}

/** 品種（HTTP 契約）。 */
enum class BreedTypeDto {
    /** サラブレッド */
    THOROUGHBRED,

    /** アラブ */
    ARAB,

    /** アングロアラブ */
    ANGLO_ARAB,

    /** サラブレッド系種 */
    THOROUGHBRED_TYPE,

    /** アラブ系種 */
    ARAB_TYPE,
}

/** DNA 型による親子判定の結果（HTTP 契約）。 */
enum class DnaParentageResultDto {
    /** 申告どおりの親子関係と矛盾しない。 */
    CONSISTENT,

    /** 申告された親子関係と矛盾する。 */
    INCONSISTENT,

    /** 未検査。 */
    UNTESTED,
}

/** HTTP 契約の性をドメインの性へ変換する。 */
fun SexDto.toDomain(): Sex =
    when (this) {
        SexDto.MALE -> Sex.MALE
        SexDto.FEMALE -> Sex.FEMALE
    }

/** ドメインの性を HTTP 契約の性へ変換する。 */
fun Sex.toApi(): SexDto =
    when (this) {
        Sex.MALE -> SexDto.MALE
        Sex.FEMALE -> SexDto.FEMALE
    }

/** HTTP 契約の毛色をドメインの毛色へ変換する。 */
fun CoatColorDto.toDomain(): CoatColor =
    when (this) {
        CoatColorDto.CHESTNUT -> CoatColor.CHESTNUT
        CoatColorDto.DARK_CHESTNUT -> CoatColor.DARK_CHESTNUT
        CoatColorDto.BAY -> CoatColor.BAY
        CoatColorDto.DARK_BAY -> CoatColor.DARK_BAY
        CoatColorDto.BROWN -> CoatColor.BROWN
        CoatColorDto.BLACK -> CoatColor.BLACK
        CoatColorDto.GRAY -> CoatColor.GRAY
        CoatColorDto.WHITE -> CoatColor.WHITE
    }

/** ドメインの毛色を HTTP 契約の毛色へ変換する。 */
fun CoatColor.toApi(): CoatColorDto =
    when (this) {
        CoatColor.CHESTNUT -> CoatColorDto.CHESTNUT
        CoatColor.DARK_CHESTNUT -> CoatColorDto.DARK_CHESTNUT
        CoatColor.BAY -> CoatColorDto.BAY
        CoatColor.DARK_BAY -> CoatColorDto.DARK_BAY
        CoatColor.BROWN -> CoatColorDto.BROWN
        CoatColor.BLACK -> CoatColorDto.BLACK
        CoatColor.GRAY -> CoatColorDto.GRAY
        CoatColor.WHITE -> CoatColorDto.WHITE
    }

/** HTTP 契約の品種をドメインの品種へ変換する。 */
fun BreedTypeDto.toDomain(): BreedType =
    when (this) {
        BreedTypeDto.THOROUGHBRED -> BreedType.THOROUGHBRED
        BreedTypeDto.ARAB -> BreedType.ARAB
        BreedTypeDto.ANGLO_ARAB -> BreedType.ANGLO_ARAB
        BreedTypeDto.THOROUGHBRED_TYPE -> BreedType.THOROUGHBRED_TYPE
        BreedTypeDto.ARAB_TYPE -> BreedType.ARAB_TYPE
    }

/** ドメインの品種を HTTP 契約の品種へ変換する。 */
fun BreedType.toApi(): BreedTypeDto =
    when (this) {
        BreedType.THOROUGHBRED -> BreedTypeDto.THOROUGHBRED
        BreedType.ARAB -> BreedTypeDto.ARAB
        BreedType.ANGLO_ARAB -> BreedTypeDto.ANGLO_ARAB
        BreedType.THOROUGHBRED_TYPE -> BreedTypeDto.THOROUGHBRED_TYPE
        BreedType.ARAB_TYPE -> BreedTypeDto.ARAB_TYPE
    }

/** HTTP 契約の DNA 判定結果をドメインの判定結果へ変換する。 */
fun DnaParentageResultDto.toDomain(): DnaParentageResult =
    when (this) {
        DnaParentageResultDto.CONSISTENT -> DnaParentageResult.CONSISTENT
        DnaParentageResultDto.INCONSISTENT -> DnaParentageResult.INCONSISTENT
        DnaParentageResultDto.UNTESTED -> DnaParentageResult.UNTESTED
    }
