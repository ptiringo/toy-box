package com.example.api.domain.studbook.model.horse.bloodhorse

import org.jmolecules.event.annotation.DomainEvent

/**
 * 軽種馬に馬名が登録された、というドメインイベント（起きたこと）。
 *
 * 馬名登録（[BloodHorse.assignName]）の成功時に生成される。`Command<T>`（何をしたいか）に対し、本型は 「何が起きたか」を表す。`@DomainEvent`
 * で役割を表明し（jMolecules のビルディングブロックの一員）、 他集約への参照は他のビルディングブロックと同じく ID 値クラス（[bloodHorseId]）経由とする。
 *
 * イミュータブル集約に合う収集・発行方式（状態遷移が [com.example.api.domain.shared.StateTransition] で 集約とイベントを同梱して返し、発行は
 * application 層が担う）は ADR-0029 を参照。発生時刻・イベント ID といったメタデータの付与は当面持たせない（最小スコープ）。
 *
 * @property bloodHorseId 命名された軽種馬の ID
 * @property name 付与された馬名
 */
@DomainEvent data class HorseNamed(val bloodHorseId: BloodHorseId, val name: HorseName)
