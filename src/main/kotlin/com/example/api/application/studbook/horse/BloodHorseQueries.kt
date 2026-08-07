package com.example.api.application.studbook.horse

import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId

/**
 * 軽種馬一覧の読み取りポート（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository] とは別物の plain
 * interface。jMolecules `@Repository` は付けない（読み取りは Repository ビルディングブロックではない）。
 * 実装（infrastructure）は集約・`BloodHorseRow` を経由せず `studbook.blood_horse` を直接読む。
 */
interface BloodHorseQueries {
    /** 指定の世界の全軽種馬を id 昇順（＝登録順。id は UUIDv7 相当）で返す（該当なしは空リスト）。 */
    fun findAll(worldId: WorldId): List<BloodHorseView>

    /**
     * 指定の世界の中から ID で単一軽種馬の詳細ビューを引く。その世界に無ければ null （単純 lookup は Result を強制しない。error-handling.md）。
     *
     * 一覧の [findAll] が返す軽量サマリ [BloodHorseView] と異なり、マイクロチップ番号と出自を含む [BloodHorseDetailView] を返す。
     */
    fun findById(worldId: WorldId, id: BloodHorseId): BloodHorseDetailView?
}
