package com.example.api.replay.fixture

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 実在馬 1 頭 × 1 繁殖シーズンの入力。
 *
 * JBIS-Search の公開記録は繁殖ワークフローが要求する項目をすべては持たない（マイクロチップ番号・DNA 型・ 証明書番号・登録番号・種付日・提出日は非公開）。そこで
 * facts（公開事実）と synthesized（手続き上必要な ため合成した値）を分け、「どこまでが実在の事実か」を後から辿れるようにする。
 *
 * 繁殖シーズンには**種付を行った年**と**種付を行わなかった年**（繁殖成績報告書 様式第14号の「種付せず」）があり、
 * 両者は同じ経路の分岐ではなく別の経路である（種付なしの年には種牡馬・種付証明書・種付成績報告・産駒が そもそも存在せず、繁殖成績は生成時点で終端になる）。この違いを型で表すため sealed
 * とし、JSON は "kind" で読み分ける。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(JsonSubTypes.Type(value = CoveredSeasonFixture::class, name = "covered"))
sealed interface HorseFixture {
    /** 突合レポートの節見出しになる名前（馬名 + シーズン）。 */
    val name: String

    /** 出典 URL。 */
    val sources: FixtureSources

    /** 合成した値の理由・断り書き。突合レポートにそのまま出る。 */
    val notes: List<String>
}

/** 種付を行った年のフィクスチャ（JSON: "kind": "covered"）。 */
data class CoveredSeasonFixture(
    override val name: String,
    override val sources: FixtureSources,
    val facts: CoveredFacts,
    val synthesized: CoveredSynthesized,
) : HorseFixture {
    override val notes: List<String>
        get() = synthesized.notes
}

/** 出典 URL。JBIS-Search の該当ページを指す。[stallion] / [foal] は種付なしの年には存在しない。 */
data class FixtureSources(
    val broodmare: String,
    val breedingRecord: String,
    val stallion: String? = null,
    val foal: String? = null,
)

/**
 * 種付を行った年の公開事実。
 *
 * [coveringYear] は種付年。JBIS の繁殖成績の年軸は「産駒の生年」なので、産駒生年から 1 を引いて求めている （この換算自体が不確実で、突合レポートの注記に出す）。
 */
data class CoveredFacts(
    val coveringYear: Int,
    val stallion: HorseFacts,
    val broodmare: HorseFacts,
    val foaling: FoalingFixture,
    val foal: FoalFacts? = null,
)

/** 基礎馬（種牡馬・繁殖牝馬）の公開事実。[originCountry] が null なら内国産（＝事実どおりの seed 経路がない）。 */
data class HorseFacts(
    val sex: String,
    val coatColor: String,
    val breedType: String,
    val dateOfBirth: String,
    val breeder: String,
    val originCountry: String? = null,
)

/**
 * [outcome] は分娩結果（FoalingOutcome）の区分名（"LiveFoal" / "NotConceived" 等）。foalingDate は LiveFoal のみ。
 *
 * JBIS は不受胎・流産・死産をすべて「産駒なし」に丸めるため、LiveFoal 以外の区分の選択は事実ではなく 合成の判断である（[CoveredSynthesized.notes]
 * に記す）。
 *
 * 種付せず（NotCovered）は分娩結果ではないのでここには現れない。種付なしの年はフィクスチャの kind で表す。
 */
data class FoalingFixture(val outcome: String, val foalingDate: String? = null)

/** 生産駒の公開事実。[name] が null なら未命名（血統登録のみで馬名登録が未了）。 */
data class FoalFacts(
    val sex: String,
    val coatColor: String,
    val breedType: String,
    val breeder: String,
    val name: String? = null,
)

/** 種付を行った年の合成値。[notes] は合成の理由で、突合レポートにそのまま出る。 */
data class CoveredSynthesized(
    val notes: List<String>,
    val stallion: HorseSynthesized,
    val broodmare: HorseSynthesized,
    val covering: CoveringFixture,
    val submissions: CoveredSubmissions,
    val foal: FoalSynthesized? = null,
)

/**
 * 基礎馬の合成値。[originCountry] / [landingDate] は内国産馬を輸入馬経路で seed するための埋め合わせでもある （現在 seed 経路は
 * RegisterImportedHorse しかない）。
 */
data class HorseSynthesized(
    val microchipNumber: String,
    val originCountry: String,
    val landingDate: String,
    val pedigreeRegistrationNumber: String,
    val breedingRegistrationNumber: String,
)

data class FoalSynthesized(
    val microchipNumber: String,
    val dnaParentage: String,
    val pedigreeRegistrationNumber: String,
)

data class CoveringFixture(
    val coveringDate: String,
    val coveringPlace: String,
    val certificateNumber: String,
    val studCertificate: StudCertificateFixture,
)

data class StudCertificateFixture(
    val number: String,
    val validRegions: List<String>,
    val validPeriodStart: String,
    val validPeriodEnd: String,
)

/** 種付を行った年の提出日。雄側（種付成績報告・当年 9/30 期限）と雌側（繁殖成績報告・翌年 5/31 期限）。 */
data class CoveredSubmissions(
    val coveringReportSubmittedOn: String,
    val breedingReportSubmittedOn: String,
)
