package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Actor
import org.springframework.stereotype.Service

/**
 * 軽種馬一覧を照会するユースケース（軽量 CQRS / L2 の読み取り側。ADR-0031）。
 *
 * 読み取りポート [BloodHorseQueries] に委譲する。コレクション照会のため失敗バリアントは設けない（該当なし＝空リスト）。 一覧も世界（セーブデータ）で絞るため [Actor]
 * を取るが、認可の判断はここでは行わない（[Actor] が組めた時点で世界の所有は 確定している。ADR-0067）。`Command` 封筒は書き込みの概念なので読み取りでは使わない。
 */
@Service
class ListBloodHorsesUseCase(private val bloodHorseQueries: BloodHorseQueries) {
    operator fun invoke(actor: Actor): List<BloodHorseView> =
        bloodHorseQueries.findAll(actor.worldId)
}
