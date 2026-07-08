# 0059. 収録曲をトラックリストで持ち見出し曲をトラックの一種として表現する

- Status: Accepted
- Date: 2026-07-09
- Deciders: Matsui

## Context（背景・課題）

[#556](https://github.com/ptiringo/toy-box/issues/556) まで、sakamichi の作品（`Single` / `Album`）は見出し曲（表題曲/リード曲）だけを `title`（`SingleTitle` / `AlbumTitle`）＋見出し曲の選抜（`Formation`）として保持し、収録曲（カップリング曲・アルバム曲・曲順）はスコープ外だった。[#565](https://github.com/ptiringo/toy-box/issues/565) で収録曲をモデル化するにあたり、見出し曲を「別枠」で残すか「トラックの一種」に寄せるかの線引きが必要になった。

## Decision（決定）

- 作品は全収録曲を `Tracklist`（`Track` = トラック番号 × 曲名の並び）として通し番号で内包する。
- 見出し曲（表題曲/リード曲）も `Track` の一種としてトラックリストに含め、作品集約は `headlineTrackNumber` で見出しを指す。見出し曲名は `headlineTitle` として導出する。
- 見出し曲名の VO（`SingleTitle` / `AlbumTitle`）は全トラック共通の `TrackTitle` に統合して削除する。
- トラック番号は明示 VO（`TrackNumber`）とし、「1..n の連番・重複なし」は `Tracklist.create` が検証する。曲名の重複は許容する（別バージョン等の余地）。
- 「見出し∈トラックリスト」の集約不変条件は `Single.create` / `Album.create` を fallible 化して自己検証する（2 つの検証済み VO 間の関係で、どちらの VO 単体でも守れないため集約ファクトリが所有。[ADR-0014](0014-self-validating-factory-over-confinement.md)）。
- 曲ごとのフォーメーション/参加メンバーは今回スコープ外（見出し曲の編成のみ作品集約が持つ）。

## Consequences（結果・影響）

- 収録曲・曲順が一級のモデルになり、見出し曲がトラックリストと二重管理されない。
- `Single.create` / `Album.create` が `Result` を返すようになり、既存呼び出し（サービス・テスト）が追従した。影響はドメイン層に限定（application / controller 配線は未着手）。
- 実ディスクの通し番号（表題曲を 1 とする等）は `Track` の番号で表現できるが、見出しの位置は `headlineTrackNumber` に委ね、番号自体の意味付け（先頭が見出し等）は強制しない。

## 関連

- [ADR-0009](0009-immutable-aggregates.md) / [ADR-0014](0014-self-validating-factory-over-confinement.md): 集約・VO の責務分担
- [#565](https://github.com/ptiringo/toy-box/issues/565): 起票元。収録曲モデル化
- [#364](https://github.com/ptiringo/toy-box/issues/364): 関連 Issue
