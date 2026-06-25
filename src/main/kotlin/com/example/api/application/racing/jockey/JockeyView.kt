package com.example.api.application.racing.jockey

import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * ジョッキーの読み取り専用ビュー（Read Model）。軽量 CQRS（L2）の読み取り側を表す（ADR-0031）。
 *
 * 書き込み側の集約 [com.example.api.domain.racing.model.jockey.Jockey] を**一切経由せず**、ストアから直接組む 平坦な
 * DTO。不変条件を持たず（検証は書き込み側のファクトリの責務）、値としての等価性が自然なため `data class` を使う（集約の ID ベース `final equals` とは無関係）。
 *
 * `@QueryModel`（jMolecules CQRS）で読み取りモデルとしての役割を表明する。DDD ビルディングブロック （`@AggregateRoot` 等）と異なり読みモデルは
 * domain.*.model ではなく application 層に置く（ArchUnit `queryModelsResideInApplication` で強制）。
 *
 * @property id ジョッキーの生 UUID
 * @property firstName 名
 * @property lastName 姓
 */
@QueryModel data class JockeyView(val id: UUID, val firstName: String, val lastName: String)
