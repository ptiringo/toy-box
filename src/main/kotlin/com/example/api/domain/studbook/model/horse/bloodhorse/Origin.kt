package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 軽種馬の出自。
 *
 * 内国産（父・母がともに当システムに血統登録済み）・輸入（父母不明で原産国・揚陸日を持つ）・移行取り込み（先行原簿に血統記録済みで父母 ID も原産国・揚陸日も持たない）は
 * 相互排他であり、一方の属性を持つときもう一方は持たない。#267（PR #358）では `sireId` / `damId` / `originCountry` / `landingDate`
 * の nullable フィールド4つ平置きで表したが、全 null や混在といった無効状態を型で防げなかった。 これを sealed interface
 * に統合し、相互排他をコンパイル時に強制する（[Domestic] / [Imported] / [CarriedOver] のいずれか一方のみ）。
 *
 * 父・母は別集約（別個体）であり、[Domestic] は集約直接参照ではなく [BloodHorseId] 経由の ID 参照で保持する。 経緯は ADR-0020 を参照。
 */
@ValueObject
sealed interface Origin {
    /**
     * 内国産馬の出自。父・母がともに当システムに血統登録済みで、それぞれの軽種馬IDで参照する。
     *
     * @property sireId 父（雄）の軽種馬ID
     * @property damId 母（雌）の軽種馬ID
     */
    @ValueObject data class Domestic(val sireId: BloodHorseId, val damId: BloodHorseId) : Origin

    /**
     * 輸入馬・基礎輸入馬の出自。父母が当システムに存在しないため父母 ID は持たず、原産国と揚陸日を持つ。
     *
     * @property originCountry 原産国
     * @property landingDate 揚陸日
     */
    @ValueObject
    data class Imported(val originCountry: OriginCountry, val landingDate: LandingDate) : Origin

    /**
     * 移行取り込み（carried-over）の出自。先行する登録原簿に血統が記録済みで、父母は当システムに存在しない。
     *
     * JAIRS の業務手続ではなく、既登録馬をシステム境界で取り込む移行経路（#633）。制度上、登録時期を
     * 過ぎた内国産馬の遡及登録・旧血統書からの移行手続は存在しない（登録規程実施基準の原典裏取り済み。
     * ADR-0069）。「血統は先行原簿に記録済み」という事実だけを型で表し、追加の属性は持たない。
     */
    @ValueObject data object CarriedOver : Origin
}
