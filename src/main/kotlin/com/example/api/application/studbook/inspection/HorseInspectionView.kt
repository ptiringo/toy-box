package com.example.api.application.studbook.inspection

import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 審査の読み取り専用ビュー（Read Model）。軽量 CQRS（L2）の読み取り側を表す（ADR-0031）。
 *
 * 書き込み側の集約 [com.example.api.domain.studbook.model.inspection.HorseInspection] を**一切経由せず**、
 * ストアから直接組む DTO。不変条件を持たず（検証は書き込み側のファクトリの責務）、値としての等価性が自然なため `data class` を使う。
 *
 * [JockeyView][com.example.api.application.racing.jockey.JockeyView] は全プリミティブだが、本ビューは sealed な
 * 親子判定の判別子語彙を application / controller に文字列で漏らさないため、検証を持たないドメイン VO （[ParentageDetermination] /
 * [IdentificationFeatures]）をそのまま載せる（集約は経由しない＝L2 の要点は維持）。
 *
 * @property id 審査の生 UUID
 * @property microchipNumber マイクロチップ番号（15 桁数字）
 * @property parentage 親子判定
 * @property features 特徴記述子。未記録なら null
 */
@QueryModel
data class HorseInspectionView(
    val id: UUID,
    val microchipNumber: String,
    val parentage: ParentageDetermination,
    val features: IdentificationFeatures?,
)
