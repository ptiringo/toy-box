# 0011. Issue 優先度を GitHub Projects のカスタムフィールドで管理する（ラベル運用を廃止）

- Status: Accepted
- Date: 2026-06-20
- Deciders: Matsui

## Context（背景・課題）

これまで Issue の優先度は `P1: 今すぐ`〜`P4: 探索・保留` の **ラベル**で運用していた（issue #335）。ラベルはリポジトリ横断で検索でき、Issue 本体に直接ぶら下がり、`gh issue list` でも見える利点がある。一方で「優先度を軸にした見方」——ソート・グルーピング・ビューでの絞り込み——がしづらい。優先度順に並べたい、P1 だけ束ねて眺めたい、といった操作がラベルでは弱い。

GitHub には優先度管理に使える機能が複数あり、用語が紛らわしいので整理した。

| 機能 | 付与対象 | 優先度用途 |
|------|---------|-----------|
| Custom properties（カスタムプロパティ） | **リポジトリ**（Org 単位のメタデータ） | ✗ Issue には付かない。リポジトリ分類用 |
| **Projects のカスタムフィールド** | Project に追加した Issue/PR | ◎ 優先度の定番。single-select（P1/P2…）でソート・グルーピング・フィルタ可 |
| Issue Types | Issue（Org 単位、Bug/Feature/Task 等） | △ 「種別」であって優先度ではない |
| ラベル | Issue / PR | ○ 可視・検索・横断に強いが、ビューでのソート/グルーピングが弱い |

「カスタムプロパティ」はリポジトリ分類用で Issue 優先度には使えない。Issue Types は種別であって優先度ではない。優先度に向くのは **Projects V2 の single-select カスタムフィールド**で、公式 Project テンプレートにも `Priority` フィールドが標準で入る。

論点は「ラベル継続 / Projects 移行 / 併用」のどれを採るか。併用は両機能の長所を取れるが、優先度の出所が 2 つになり手動同期の二重管理が生じる（ラベルを変えたらフィールドも、の往復が必要で、必ずズレる）。個人リポジトリ（toy-box）であり、出所は 1 つに保ちたい。

## Decision（決定）

**Issue 優先度は GitHub Projects のカスタムフィールド（single-select）を唯一の出所とし、`P1〜P4` ラベルは廃止する（完全移行）。**

- 優先度フィールドは toy-box リポジトリの Project #4（`toy-box`）に `Priority` single-select フィールドとして定義する。オプションは従来のラベルと同じ語彙 `P1: 今すぐ` / `P2: 近いうち` / `P3: いずれ` / `P4: 探索・保留` を踏襲し、語彙の継続性を保つ。
- 既存の open issue は移行スクリプト（`scripts/migrate-priority-to-project.sh`）で Project へ投入し、ラベルの優先度を Priority フィールドへ写像したうえで、`P1〜P4` ラベル定義をリポジトリから削除する。
- 以後の優先度設定は Project のビュー（または `gh project item-edit`）で行う。`gh issue list` の出力からは優先度が見えなくなるため、優先度順に眺めたいときは Project のビューを使う。
- Issue Types（Bug/Feature/Task 等の種別）は優先度とは直交する別軸であり、本決定の対象外（必要になれば別途検討）。

採否の結論と日常運用は [CLAUDE.md](../../CLAUDE.md)「優先度管理」に置く。

## Consequences（結果・影響）

- 優先度の出所が Project の Priority フィールド 1 つに一本化され、ラベルとの二重管理・手動同期のズレが消える。
- Project のビューで優先度ソート・グルーピング・フィルタが使えるようになり、「P1 だけ束ねて見る」「優先度順に並べる」が容易になる。
- 引き換えに、`gh issue list` や Issue 一覧画面のラベル列からは優先度が見えなくなる。優先度を見るには Project（Web のビュー、または `gh project item-list 4 --owner ptiringo`）を経由する必要がある。CLI で優先度を素早く確認する用途はやや後退する。
- Project に入れた Issue だけが優先度を持つ。新規 Issue は Project へ追加して初めて優先度を付けられる（運用として、優先度を付けたい Issue は Project に入れる）。
- ラベル定義の削除により、closed issue も含め全 Issue から `P1〜P4` ラベルが外れる。closed issue の当時の優先度ラベルは失われるが、完了済みのため実害はない。
- 移行・フィールド定義は `gh project` CLI で再現可能な形にしてある（スクリプト化）。将来 Project を作り直す場合もスクリプトを起点に再構築できる。
