---
name: hcp-run-inspect
description: Use when checking a Terraform plan/run in HCP Terraform via tfctl — especially the speculative plan that ran for a PR（「PR の plan を確認」「HCP の run を見る」「terraform plan が通ったか」plan の add/change/destroy 数）。PR の run が既定の run 一覧に見当たらないときも。plan の差分の中身（どのリソースのどの属性か）を属性レベルで突き止めたいとき、とくに「毎回同じ差分が出る」「apply しても消えない」永久差分を調べるときにも使う。
---

# HCP Terraform の run/plan を tfctl で確認する

## Overview

toy-box の infra は HCP Terraform（org `ptiringo-tech` / workspace `toy-box`。`infra/terraform.tf`）で実行される。PR の plan 結果や run の状態を **tfctl の読み取り操作**で確認する手順。

## 前提

- tfctl は **Bash sandbox の外**で実行する必要がある（sandbox 内だと HCP API が TLS 検証エラー `OSStatus -26276` で失敗）。ローカル設定で tfctl を sandbox 除外しておく（gh と同じ扱い）。
- `tfctl auth login` 済みであること（`tfctl get workspaces -o ptiringo-tech` が通れば OK）。
- ここで使うのは読み取り（`tfctl get` / `tfctl api`（GET）/ `tfctl run status`）。`tfctl run start` / `variable import` / `create` / `api`（変更）は変更系で確認（ask）が入る（[ADR-0036](../../../docs/adr/0036-gcp-operation-guardrails.md)、運用は `.claude/rules/gcp-guardrails.md`）。tfctl 採用の経緯は [ADR-0034](../../../docs/adr/0034-adopt-tfctl-cli.md)。

## 肝（落とし穴）

HCP の **既定の run 一覧は PR（speculative）run を返さない**。`GET /workspaces/:id/runs` は VCS の post-merge run（`source=tfe-configuration-version`）しか出さないため、これだけ見て「PR の plan が無い」と誤認しやすい。**PR の run はコミット SHA で引く**（`search[commit]`）。

## 手順: PR の plan を確認する

```bash
# 1) workspace ID を確認（現状 ws-U3Mfb1ycNvfbshU7。変わったら引き直す）
tfctl get workspaces -o ptiringo-tech        # toy-box 行の ID 列

# 2) PR の head SHA
sha=$(gh pr view <PR番号> --json headRefOid -q .headRefOid)

# 3) その commit の run を引く（既定一覧では出ない。search[commit] が鍵。[ ] は %5B/%5D）
tfctl api "/workspaces/ws-U3Mfb1ycNvfbshU7/runs?search%5Bcommit%5D=$sha" \
  --jq '.data[] | {id:.id, status:.attributes.status, msg:.attributes.message}'

# 4) run → plan → 変更数
plan=$(tfctl api "/runs/<run-id>" --jq '.data.relationships.plan.data.id')
tfctl api "/plans/$plan" --jq '{add:.data.attributes."resource-additions", change:.data.attributes."resource-changes", destroy:.data.attributes."resource-destructions", status:.data.attributes.status}'
```

`planned_and_finished` + 期待どおりの add/change/destroy なら plan は健全。作成/変更されるリソース名の一覧が要るときは HCP UI の run ページ（`run status` の link）か run のログを見る。

## 手順: PR の run を回し直す

**`tfctl run start` では回し直せない**。`--help` が「creates a new plan and apply run with the **most recent configuration**」と明記しているとおり、対象は workspace の最新 configuration（＝ main の内容）で、PR ブランチではない。configuration version を指定するフラグも無い（`--plan-only` は「apply しない」を意味するだけで、対象を PR に変える機能ではない）。

PR の configuration version のまま回し直すには、**HCP UI の run ページで "Retry this run" を押す**。

```
https://app.terraform.io/app/ptiringo-tech/workspaces/toy-box/runs/<run-id>
```

回し直しが要る典型は **新しい変数を足した PR**。変数を HCP workspace に登録する前に push すると plan が `No value for required variable` で errored になり、**その後に変数を登録しても run は自動では回り直さない**。retry するか、別の変更を新しいコミットとして push する（push すれば VCS 連携で新しい run が走る）。

## 手順: 差分の中身（どの属性が変わるのか）を見る

`resource-changes` は件数、run のログ（`type: planned_change`）はリソース名までしか出ない。**どの属性が差分なのかは plan の JSON 出力で before/after を突き合わせる**。「毎回同じリソースが update される」の原因究明はここまで落とさないと分からない。

```bash
plan=$(tfctl api "/runs/<run-id>" --jq '.data.relationships.plan.data.id')
tfctl api "/plans/$plan/json-output" > plan.json   # ← 変数値が平文で入る。scratchpad に置き、用が済んだら消す

# no-op 以外のリソース（＝実際に変更が計画されているもの）
jq '[.resource_changes[] | select(.change.actions != ["no-op"]) | {addr:.address, actions:.change.actions}]' plan.json

# 対象リソースの before/after のうち、実際に食い違うトップレベルのキーだけを出す
jq -r '.resource_changes[] | select(.address=="<addr>") | .change | {before,after}' plan.json > d.json
jq -n --slurpfile d d.json '$d[0].before as $b | $d[0].after as $a
  | [($b|keys[]) | select(($b[.]|tojson) != ($a[.]|tojson)) | {key:., before:$b[.], after:$a[.]}]'
```

`resource_drift[]` も同じ構造で、refresh で検出されたサーバ側の変化（ログの `Drift detected`）が入る。**drift は plan の変更数には計上されない**ので、`update_time` のような読み取り専用属性だけが動いていれば実害はなく、設定で抑止する手段も基本ない。

**注意: `json-output` には workspace の変数値が平文で含まれる**（sensitive 指定のトークン類も `.variables` に入る）。貼り付け・共有はせず、scratchpad に落として読み終えたら削除する。

## Quick Reference

| 目的 | コマンド |
|---|---|
| workspace 一覧 / ID | `tfctl get workspaces -o ptiringo-tech` |
| PR の head SHA | `gh pr view <n> --json headRefOid -q .headRefOid` |
| commit → run | `tfctl api "/workspaces/<ws>/runs?search%5Bcommit%5D=<sha>"` |
| run の状態（要約） | `tfctl run status <run-id>` |
| plan の変更数 | `tfctl api "/plans/<plan-id>"`（`resource-additions` / `-changes` / `-destructions`） |
| 差分の属性まで | `tfctl api "/plans/<plan-id>/json-output"` → `.resource_changes[].change.before/after`（変数値が平文で入る点に注意） |
| PR の run を回し直す | HCP UI の "Retry this run"（`tfctl run start` は最新 configuration が対象で使えない） |

## Common Mistakes

- 既定の run 一覧（`page%5Bsize%5D` だけ等）を見て「PR の plan が無い」と結論する → `search[commit]` で引く（**最頻**）。
- GitHub のコミットステータスの target_url は集約ステータス（`acs-...`）止まりで run-id は出ない → head SHA 経由で引く。
- `search[commit]` / `page[size]` の `[` `]` を URL エンコードし忘れる（`%5B` / `%5D`）。
- TLS エラー `OSStatus -26276` → tfctl が sandbox 外で実行されていない。`X Unauthorized for app.terraform.io` → `tfctl auth login` 未実施。
- 「毎回 `1 to change` が消えない」を件数とリソース名だけ見て原因不明のままにする → `json-output` で属性まで落とす。設定に書いていない block を API が既定値付きで返しているだけ（apply しても state に書き戻されて収束しない永久差分）というのが典型で、既定値をそのまま HCL に明示すれば止まる。
- `tfctl run start` で PR の plan を回し直そうとする → 対象は workspace の最新 configuration（= main）で、PR ブランチではない。UI の retry を使う。
