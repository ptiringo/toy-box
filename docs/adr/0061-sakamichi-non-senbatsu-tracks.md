# 0061. 非選抜曲をトラック × 編成の 0..\* コレクションとしてモデル化する

- Status: Accepted
- Date: 2026-07-09
- Deciders: Matsui

## Context（背景・課題）

[ADR-0058](0058-non-senbatsu-formation-role.md) は非選抜（アンダー/BACKS/ひなた坂46 名義）を `Single.nonSenbatsu: Formation?`（単数・任意）としてモデル化した。しかし公式典拠（各グループ公式サイト、参照日 2026-07-09）を確認すると、非選抜は**曲ごとに独自の編成を持つカップリング曲**であり、1 作品に複数曲収録されることが珍しくない（例: 櫻坂46 の両 A 面シングルは非選抜曲を 2 曲収録し、曲ごとにセンター・フォーメーションが異なる）。ADR-0058 の単数・トラック非連結のモデルでは、この「トラックごとに独自編成を持つ 0..\* の非選抜曲」を表現できない。また ADR-0058 は「非選抜楽曲はほぼシングルの C/W のため」という理由で `Album` を対象外としたが、アルバムにも非選抜曲は収録されうるため、この前提は狭すぎる。

[#565](https://github.com/ptiringo/toy-box/issues/565)（[ADR-0059](0059-sakamichi-tracklist-and-headline-track.md)）で導入した `Tracklist` / `Track` により、作品は全収録曲をトラック番号で内包するようになった。非選抜曲はこのトラックリストの一部（見出し曲以外のいずれかのトラック）として位置づけられるべきである。

## Decision（決定）

- 新しい値オブジェクト `NonSenbatsuTrack(trackNumber: TrackNumber, formation: Formation)`（`domain.sakamichi.model.release`）を導入する。非選抜曲 1 曲＝「どのトラックを」「どの編成で」歌うかを表す。
- `Single` / `Album` はいずれも `nonSenbatsuTracks: List<NonSenbatsuTrack>`（0..\*、デフォルト空リスト＝全員選抜）を保持する。**`Single.nonSenbatsu: Formation?` は廃止し、Album も対称に対応する**（ADR-0058 の「単数」「Album 非対応」を supersede）。
- `Track`（トラック番号 × 曲名）は純粋なまま維持し、曲ごとの編成を `Track` 自体には持たせない。曲ごとの編成情報は非選抜曲（`NonSenbatsuTrack`）にのみ局所化する（見出し曲の編成は既存どおり集約の `senbatsu: Formation` が持つ）。
- 集約ファクトリ（`Single.create` / `Album.create`）は次の不変条件を検証する（複数の検証済み VO 間の関係で、どの VO 単体でも守れないため集約ファクトリが所有。[ADR-0014](0014-self-validating-factory-over-confinement.md)）:
  1. 非選抜曲のトラック番号がトラックリストに存在する
  2. 非選抜曲のトラック番号が見出しトラックと重複しない（非選抜曲は見出し曲ではない）
  3. 非選抜曲のトラック番号同士が重複しない（同一トラックを複数の非選抜曲として指定しない）
- **排他は「選抜とのみ」課す。非選抜曲同士の重複（同一メンバーが複数の非選抜曲に跨って出演すること）は許容する**（曲単位の編成という性質上、非選抜曲間の重複は実態として起こりうるため制約しない）。選抜対象メンバーの在籍検証・選抜×非選抜の排他は、ADR-0058 と同様に集約をまたぐ前提条件のためドメインサービス（`releaseSingle` / `releaseAlbum`）が検証する。

## Consequences（結果・影響）

- `Single.nonSenbatsu` を破壊的に置き換える（`Formation?` → `nonSenbatsuTracks: List<NonSenbatsuTrack>`）。`releaseSingle` の入力もトラック単位の複数編成を受け取る形へ変わる（呼び出し元・テストが追従済み）。
- `Album` が非選抜曲に対応する。ADR-0058 の「非選抜楽曲はほぼシングルの C/W のため `Album` は対象外」という前提を撤回する。
- 呼称の時間軸（グループ別呼称がいつから適用されるか、[#582](https://github.com/ptiringo/toy-box/issues/582) が別 Issue に委譲済み）・非選抜ライブ・期別曲（センセーションなど）は引き続き別 Issue のスコープとする。

## Supersedes

本 ADR は [ADR-0058](0058-non-senbatsu-formation-role.md) の Decision のうち「`Single` が `nonSenbatsu: Formation?` の単一・任意ロールとして非選抜を持つ」「`Album` は非選抜楽曲を持たない（`Formation` リネームのみ）」の 2 点を supersede する。ADR-0058 の残りの決定（選抜と非選抜を中立の `Formation` へ汎用化する、排他と在籍検証はドメインサービスが担う等）は引き続き有効。

## 関連

- [ADR-0009](0009-immutable-aggregates.md) / [ADR-0014](0014-self-validating-factory-over-confinement.md) / [ADR-0022](0022-domain-service-repository-for-set-invariants.md): 集約・VO・ドメインサービスの責務分担
- [ADR-0058](0058-non-senbatsu-formation-role.md): 非選抜を作品単位の `Formation` ロールとしてモデル化する決定（本 ADR が一部を supersede）
- [ADR-0059](0059-sakamichi-tracklist-and-headline-track.md): 収録曲をトラックリストで持つ決定（本 ADR の前提）
- [#583](https://github.com/ptiringo/toy-box/issues/583): 起票元
