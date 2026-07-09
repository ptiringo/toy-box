# 0058. 非選抜（アンダー）を作品単位の Formation ロールとしてモデル化する

- Status: Superseded by [ADR-0061](0061-sakamichi-non-senbatsu-tracks.md)
- Date: 2026-07-08
- Deciders: Matsui

## Context（背景・課題）

選抜（`Senbatsu`）は作品単位の VO としてモデル化済みだったが、その対概念である非選抜（アンダー）は未モデル化だった（[#556](https://github.com/ptiringo/toy-box/issues/556)）。非選抜をどう位置づけるか（選抜の補集合として導出するか、一級概念として持つか）が論点。

公式典拠（各グループ公式サイト、参照日 2026-07-08）で次を確認した:
- 「アンダー」は乃木坂46 固有の呼称で、櫻坂46 は「BACKS」、日向坂46 は「ひなた坂46（ひらがなひなた、11th 以降）」と呼称が異なる。3 グループとも非選抜の活動体・独自センター/フォーメーションを持つ。
- 非選抜はグループ恒久属性ではなく作品単位・時期依存の編成（全員選抜の作品もある）。厳密な排他集合として固定はできない（作品をまたぐと重複しうる）。
- 在籍者集合は書き込み集約に無い（`Group` は在籍を持たず名簿は Read Model 前提）。純粋な「在籍 − 選抜」導出は集約単体では不能。

## Decision（決定）

- 非選抜を**作品単位の一級の編成**として持つ。選抜と非選抜は構造が同型（センター＋立ち位置）でロールが違うだけなので、選抜 VO `Senbatsu` を**中立の `Formation` へ汎用化**し、`Single` が `senbatsu: Formation` と `nonSenbatsu: Formation?`（任意）の 2 ロールで内包する（ロールは型でなくフィールドで表す）。
- 純粋導出は採らない（在籍集合が集約外で不能）。非選抜編成はドメインサービス `releaseSingle` が編成時に確定する。
- **選抜×非選抜の排他**（同一作品で同一メンバーが両方に立たない）と両編成の在籍検証は `releaseSingle` が担う（既存の在籍検証と同じ場所。集約 `create` は非検証のまま）。
- **グループ別の呼称**（アンダー/BACKS/ひなた坂）は本 ADR のスコープ外とし、別 Issue で `Group` 属性としてモデル化する。非選抜楽曲・ライブの一級概念化も別 Issue。
- `Album` は `Formation` リネームのみ（非選抜楽曲はほぼシングルの C/W のため `nonSenbatsu` は追加しない）。

## Consequences（結果・影響）

- 特定グループの呼称（アンダー等）をハードコードせず、3 グループに中立なモデルになる。呼称差はフォローアップで `Group` 属性として吸収する。
- `Senbatsu`→`Formation` の横断リネームが発生した（single/album/service/tests/用語集）。「選抜/非選抜」のユビキタス言語はフィールド名・サービス API・用語集で表現する。
- 排他をサービス側に置くため、`Single.create` を直接呼ぶ経路では排他が保証されない（既存の在籍検証と同じトレードオフ）。正規の生成経路は `releaseSingle`。
- 非選抜は任意（`Formation?`）なので全員選抜・非選抜制度を持たない時期/グループに強制しない。

## 関連

- [ADR-0009](0009-immutable-aggregates.md) / [ADR-0014](0014-self-validating-factory-over-confinement.md) / [ADR-0022](0022-domain-service-repository-for-set-invariants.md): 集約・VO・ドメインサービスの責務分担
- [#556](https://github.com/ptiringo/toy-box/issues/556): 起票元。呼称・楽曲/ライブのフォローアップ Issue はここから辿る
