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
@JsonSubTypes(
    JsonSubTypes.Type(value = CoveredSeasonFixture::class, name = "covered"),
    JsonSubTypes.Type(value = UncoveredSeasonFixture::class, name = "uncovered"),
)
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
 * [displayedYear] は JBIS の繁殖成績に表示されている年。年軸は「産駒の生年」であって種付年ではないため、 種付年そのものは公開されていない（表示年から 1 を引いて求めた
 * [CoveredSynthesized.coveringYear] が合成値）。
 */
data class CoveredFacts(
    val displayedYear: Int,
    val stallion: HorseFacts,
    val broodmare: HorseFacts,
    val foaling: FoalingFixture,
    val foal: FoalFacts? = null,
)

/**
 * 基礎馬（種牡馬・繁殖牝馬）の公開事実。
 *
 * [originCountry] は内国産馬なら「日本」。JBIS の産地から分かる事実なので、輸入馬・内国産馬のいずれでも必ず書く （書き忘れは読み込み時に落ちる）。seed
 * の経路はこの事実から導出する: 「日本」なら移行取り込み、 それ以外なら輸入馬経路（#633。導出の根拠は FixtureMappers.isDomestic の KDoc）。
 */
data class HorseFacts(
    val sex: String,
    val coatColor: String,
    val breedType: String,
    val dateOfBirth: String,
    val breeder: String,
    val originCountry: String,
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

/**
 * 種付を行った年の合成値。[notes] は合成の理由で、突合レポートにそのまま出る。
 *
 * [coveringYear] は種付年。JBIS の年軸は産駒の生年なので、[CoveredFacts.displayedYear] から 1 を引いて求めた
 * 換算値であり、公開された事実ではない。
 */
data class CoveredSynthesized(
    val notes: List<String>,
    val coveringYear: Int,
    val stallion: HorseSynthesized,
    val broodmare: HorseSynthesized,
    val covering: CoveringFixture,
    val submissions: CoveredSubmissions,
    val foal: FoalSynthesized? = null,
)

/**
 * 基礎馬の合成値。
 *
 * 出生国はここには無い（内国産馬の「日本」も含めて事実なので [HorseFacts.originCountry] が持つ）。 [landingDate]
 * は輸入馬経路（RegisterImportedHorse）で seed する外国産の基礎馬のみが持つ（輸入登録は 実際に起きた事実だが、輸入年月日は JBIS
 * 非公開のため合成）。内国産の基礎馬は移行取り込み経路 （RegisterCarriedOverHorse）で seed するため揚陸日を持たない（#633）。
 */
data class HorseSynthesized(
    val microchipNumber: String,
    val landingDate: String? = null,
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

/**
 * 種付を行わなかった年のフィクスチャ（JSON: "kind": "uncovered"）。
 *
 * 繁殖成績報告書 様式第14号「子馬のない母の繁殖成績」の 8 区分目（種付せず）にあたる年。種牡馬・種付証明書・ 種付成績報告・産駒がそもそも存在しないため、それらの欄を持たない。
 */
data class UncoveredSeasonFixture(
    override val name: String,
    override val sources: FixtureSources,
    val facts: UncoveredFacts,
    val synthesized: UncoveredSynthesized,
) : HorseFixture {
    override val notes: List<String>
        get() = synthesized.notes
}

/**
 * 種付を行わなかった年の公開事実。
 *
 * [displayedYear] は JBIS の繁殖成績に表示されている年（この行は産駒なし・種牡馬欄が空）。 産駒も種付も存在しないため、この年に対応する繁殖年は公開記録から導けない
 * （[UncoveredSynthesized.breedingYear] が換算値）。
 */
data class UncoveredFacts(val displayedYear: Int, val broodmare: HorseFacts)

/**
 * 種付を行わなかった年の合成値。種付が無いので種付証明書も種付成績報告も持たない。
 *
 * [breedingYear] は繁殖年。種付が無いので種付日から導出できず、明示的に持つ（RecordUncoveredCommand も同様）。 種付ありの年と同じ換算（表示年 −
 * 1）で求めているが、産駒も種付も存在しない行にこの換算を当ててよいかは 公開記録から判別できない（＝合成の判断であり、[notes] に記す）。
 */
data class UncoveredSynthesized(
    val notes: List<String>,
    val breedingYear: Int,
    val broodmare: HorseSynthesized,
    val submissions: UncoveredSubmissions,
)

/** 種付を行わなかった年の提出日。雌側の繁殖成績報告（翌年 5/31 期限）のみ。 */
data class UncoveredSubmissions(val breedingReportSubmittedOn: String)
