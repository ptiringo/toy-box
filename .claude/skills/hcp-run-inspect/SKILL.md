---
name: hcp-run-inspect
description: Use when checking a Terraform plan/run in HCP Terraform via tfctl — especially the speculative plan that ran for a PR（「PR の plan を確認」「HCP の run を見る」「terraform plan が通ったか」plan の add/change/destroy 数）。PR の run が既定の run 一覧に見当たらないときも。
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

## Quick Reference

| 目的 | コマンド |
|---|---|
| workspace 一覧 / ID | `tfctl get workspaces -o ptiringo-tech` |
| PR の head SHA | `gh pr view <n> --json headRefOid -q .headRefOid` |
| commit → run | `tfctl api "/workspaces/<ws>/runs?search%5Bcommit%5D=<sha>"` |
| run の状態（要約） | `tfctl run status <run-id>` |
| plan の変更数 | `tfctl api "/plans/<plan-id>"`（`resource-additions` / `-changes` / `-destructions`） |

## Common Mistakes

- 既定の run 一覧（`page%5Bsize%5D` だけ等）を見て「PR の plan が無い」と結論する → `search[commit]` で引く（**最頻**）。
- GitHub のコミットステータスの target_url は集約ステータス（`acs-...`）止まりで run-id は出ない → head SHA 経由で引く。
- `search[commit]` / `page[size]` の `[` `]` を URL エンコードし忘れる（`%5B` / `%5D`）。
- TLS エラー `OSStatus -26276` → tfctl が sandbox 外で実行されていない。`X Unauthorized for app.terraform.io` → `tfctl auth login` 未実施。
