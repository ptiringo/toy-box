package com.example.api.domain.sakamichi.model.release

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 非選抜楽曲 1 曲。作品内のどのトラック（[trackNumber]）を、どの編成（[formation]）で歌うか。
 *
 * 非選抜曲（アンダー曲/BACKS曲/ひなた坂46 名義曲）は表題曲を歌わないメンバーによるカップリング曲で、
 * 曲ごとに独自のセンター・フォーメーションを持つ。曲ごとの編成をこの非選抜曲だけに局所化し、 表題曲以外の全トラックへは広げない（[Track] は曲名＋番号のまま純粋に保つ。ADR-0059 /
 * ADR-0061）。
 *
 * 検証済みの VO を受け取るため単体では失敗しない。作品内での関係（トラックリストに存在する・ 見出し曲ではない・重複しない）は作品集約（`Single` /
 * `Album`）の生成ファクトリが守る（ADR-0014）。 「非選抜曲のメンバーが選抜と排他であること」は集約をまたぐ前提のためドメインサービスが守る。
 *
 * @property trackNumber 非選抜曲のトラック番号
 * @property formation 非選抜曲の編成（アンダーセンター等）
 */
@ValueObject data class NonSenbatsuTrack(val trackNumber: TrackNumber, val formation: Formation)
