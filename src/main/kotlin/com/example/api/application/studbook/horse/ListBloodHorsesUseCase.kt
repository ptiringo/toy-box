package com.example.api.application.studbook.horse

import org.springframework.stereotype.Service

/**
 * 軽種馬一覧を照会するユースケース（軽量 CQRS / L2 の読み取り側。ADR-0031）。
 *
 * 読み取りポート [BloodHorseQueries] に委譲する。コレクション照会のため失敗バリアントは設けない（該当なし＝空リスト）。 読み取りは認証のみで認可不要のため `Actor`
 * を取らず、`Command` 封筒も使わない。
 */
@Service
class ListBloodHorsesUseCase(private val bloodHorseQueries: BloodHorseQueries) {
    operator fun invoke(): List<BloodHorseView> = bloodHorseQueries.findAll()
}
