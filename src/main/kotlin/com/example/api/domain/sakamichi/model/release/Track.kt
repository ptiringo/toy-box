package com.example.api.domain.sakamichi.model.release

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 収録曲 1 曲（トラック）。作品内でのトラック番号（[number]）と曲名（[title]）の対。
 *
 * 検証済みの VO を受け取るため単体では失敗しない。作品内での番号の一意性・連番は [Tracklist.create] が守る。
 * 曲ごとのフォーメーション/参加メンバーは現時点でスコープ外（見出し曲の編成のみ作品集約が持つ）。
 *
 * @property number トラック番号
 * @property title 曲名
 */
@ValueObject data class Track(val number: TrackNumber, val title: TrackTitle)
