package com.example.api.replay.fixture

/** 実在馬 1 頭 × 1 繁殖シーズンの公開事実。ワークフロー入力への逆算元。 ドメインの Command そのものではなく、公開記録の素直な写し。出典は [sourceUrl] に記す。 */
data class HorseFixture(
    val name: String,
    val sourceUrl: String,
    val coveringYear: Int,
    val stallion: FoundationHorse,
    val broodmare: FoundationHorse,
    val covering: CoveringFixture,
    val foaling: FoalingFixture,
    val foal: FoalFixture?,
    val breedingReportSubmittedOn: String,
)

/** 起点となる基礎馬（親不在で seed する輸入馬経路の入力）。 */
data class FoundationHorse(
    val sex: String,
    val coatColor: String,
    val breedType: String,
    val dateOfBirth: String,
    val breeder: String,
    val microchipNumber: String,
    val originCountry: String,
    val landingDate: String,
    val pedigreeRegistrationNumber: String,
    val breedingRegistrationNumber: String,
)

data class CoveringFixture(
    val coveringDate: String,
    val coveringPlace: String,
    val certificateNumber: String,
    val studCertificate: StudCertificateFixture,
    val reportSubmittedOn: String,
)

data class StudCertificateFixture(
    val number: String,
    val validRegions: List<String>,
    val validPeriodStart: String,
    val validPeriodEnd: String,
)

/**
 * [outcome] は FoalingOutcome の区分名（"LiveFoal" / "NotConceived" / "Abortion" 等）。 foalingDate は
 * LiveFoal のみ。
 */
data class FoalingFixture(val outcome: String, val foalingDate: String?)

/** 生産駒の登録情報。outcome が LiveFoal のときだけ非 null。 */
data class FoalFixture(
    val sex: String,
    val coatColor: String,
    val breedType: String,
    val breeder: String,
    val microchipNumber: String,
    val dnaParentage: String,
    val pedigreeRegistrationNumber: String,
    val name: String,
)
