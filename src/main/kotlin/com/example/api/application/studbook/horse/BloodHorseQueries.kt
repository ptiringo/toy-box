package com.example.api.application.studbook.horse

/**
 * 軽種馬一覧の読み取りポート（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込みポート [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository] とは別物の plain
 * interface。jMolecules `@Repository` は付けない（読み取りは Repository ビルディングブロックではない）。
 * 実装（infrastructure）は集約・`BloodHorseRow` を経由せず `studbook.blood_horse` を直接読む。
 */
interface BloodHorseQueries {
    /** 登録済みの全軽種馬を id 昇順（＝登録順。id は UUIDv7 相当）で返す（該当なしは空リスト）。 */
    fun findAll(): List<BloodHorseView>
}
