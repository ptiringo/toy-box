---
name: issue-ops
description: toy-box で GitHub Issue を扱うときの操作手順集。次にやる Issue を選ぶ・候補を一覧する／Issue を新規作成して Project #4 に追加し優先度を設定する／優先度を変更する、のいずれか。「イシュー何かやろう」「次やるのは？」「優先度高いの教えて」「Issue 立てて」「優先度を P2 に」等が合図。クローズ済み・実装済みを候補に出さないための手順と、Project #4（Priority/Status カスタムフィールド）の操作 ID をここに集約する。
---

# issue-ops（toy-box の Issue 操作手順）

toy-box の Issue は **GitHub Projects #4（owner: ptiringo）** の `Priority` / `Status` single-select カスタムフィールドで
管理する（ラベルではない。ADR-0011 / CLAUDE.md「優先度管理」）。常時ロードの CLAUDE.md には *守るべきルール*
だけを置き、*操作のやり方*（ID・jq・確認手順）はこのスキルに集約する。

- 優先度語彙: `P1: 今すぐ` / `P2: 近いうち` / `P3: いずれ` / `P4: 探索・保留`。
- 進行状況 `Status`: `Todo` / `In Progress` / `Done`（`Done` は「Issue close → Status=Done」の Project Workflow で自動設定される派生値）。
- Project スコープが要るので、権限エラーが出たら `gh auth refresh -s project`。
- **操作 ID 実値**（2026-06-21 確認。ズレたら `gh project field-list 4 --owner ptiringo --format json` で取り直す）:
  - project-id = `PVT_kwHOAGtZ7c4BbL04`
  - Priority field-id = `PVTSSF_lAHOAGtZ7c4BbL04zhV-LEM`
  - option-id = P1:`e9ce2b26` / P2:`61f24689` / P3:`1d0a06c9` / P4:`12ac3381`
  - item-id は `gh project item-list 4 --owner ptiringo --format json` で issue number から引く。

---

## A. 次にやる Issue を選ぶ／候補を一覧する

「次に何をやるか」をユーザーに提案する前に、**必ずこの手順で候補集合を作る**。過去に Project ボードを
priority だけ見て拾い、**クローズ済み・実装済みの Issue（例: Done 済みの #346）を「未対応」として提案する失敗を繰り返した**。
原因は `gh project item-list` がクローズ済みも含み、出力に open/closed が無いこと。下記で構造的に防ぐ。

### 1. 候補集合を作る（未 Done に絞る）

`.status != "Done"`（着手前だけ見たいなら `== "Todo"`）で絞る。1 回の呼び出しで priority + status が揃い join 不要。

```bash
gh project item-list 4 --owner ptiringo --format json --limit 200 \
  | jq -r '.items[] | select(.content.type=="Issue" and .status != "Done")
      | "\(.priority // "未設定")\t\(.status)\t#\(.content.number)\t\(.content.title)"' | sort
```

優先度順に整列したいときは `.priority` を `P1..P4 → 0..3` に写してソートする。

### 2. 動的な絞り込み（ラベル / キーワード / 担当）

「security の」「breeding 周りの」等の条件付き依頼は、一次情報の `gh issue list` で絞ってから priority を付ける:

```bash
gh issue list --state open --label security --search "breeding" --json number,title
```

`gh issue list` に優先度列は無いので、得た番号を 1 の出力と突き合わせて priority を引く。

### 3. 整合性チェック（保険・任意）

`.status` は派生値なので、たまに一次情報と件数照合する:

```bash
gh issue list --state open --limit 300 --json number -q '.[].number' | wc -l   # ↑ 1 の not-Done 件数と一致するはず
```

### 4. 特定 Issue を「やりましょう」と推す前に実装済みでないか確認【必須】

- `gh issue view <n> --json state,closed` が `OPEN` か
- linked merged PR / `closingIssuesReferences` が無いか（`gh issue view <n> --json ...` や `gh pr list --search "<n>"`）
- feature 系なら成果物がリポジトリに既にないか軽く grep（期待する型・ファイル名・docs）

### 5. 提案する

上位候補（通常は最優先の段から数件）を `AskUserQuestion` 等で提示し、ユーザーに選んでもらう。
すでに `In Progress` のものは、ユーザーが明示しない限り候補から外す。

---

## B. Issue を新規作成する（→ Project 追加 → 優先度設定まで 1 セット）

**CLAUDE.md のルール: 作成した Issue は必ず Project #4 に追加し、Priority を設定する**（未定でも Project には入れる）。
作成だけで止めない。手順:

```bash
# 1) 作成
gh issue create --title "<title>" --body "<body>"   # 返ってくる issue URL を控える

# 2) Project #4 に追加
gh project item-add 4 --owner ptiringo --url <issue-url>

# 3) 優先度を設定（item-id は number から引く。ID 実値は冒頭参照）
item=$(gh project item-list 4 --owner ptiringo --format json --limit 300 \
  | jq -r --argjson n <number> '.items[] | select(.content.number==$n) | .id')
gh project item-edit --id "$item" --project-id PVT_kwHOAGtZ7c4BbL04 \
  --field-id PVTSSF_lAHOAGtZ7c4BbL04zhV-LEM --single-select-option-id <option-id>
```

---

## C. 既存 Issue の優先度を変更する

```bash
item=$(gh project item-list 4 --owner ptiringo --format json --limit 300 \
  | jq -r --argjson n <number> '.items[] | select(.content.number==$n) | .id')
gh project item-edit --id "$item" --project-id PVT_kwHOAGtZ7c4BbL04 \
  --field-id PVTSSF_lAHOAGtZ7c4BbL04zhV-LEM --single-select-option-id <option-id>
```

---

## やってはいけないこと

- `gh project item-list` を priority だけ見て、open/closed・`.status` を無視して候補に出す（A）。
- メモリや記憶だけに頼って「たぶん未対応」と推測で提案する（必ず A-4 を実行）。
- Issue を作って Project 追加・優先度設定をせずに終える（B のルール違反）。
