# 0068. infra の HCP Terraform apply を手動承認 + 通知にする（auto_apply 無効化）

- Status: Accepted
- Date: 2026-07-21
- Deciders: Matsui

## Context（背景・課題）

infra は HCP Terraform（workspace `toy-box`）で VCS-driven に実行する。PR で speculative plan が走り、main へのマージで apply run が走る。当初 workspace は `auto_apply = true` で、**PR をマージした瞬間に apply が無確認で走る**設定だった。

[#659](https://github.com/ptiringo/toy-box/pull/659)（GCP Identity Platform の Terraform 化）で `google_identity_platform_config` を作る際、このリソースが **API 上一度作ると削除できない（恒久）** ことに気づいた。`auto_apply = true` のままでは、マージ ＝ 不可逆な GCP リソース作成が人の確認なしに走る。これは [ADR-0036](0036-gcp-operation-guardrails.md)（Claude Code から GCP を触るときのガードレール）が掲げる「**不可逆・課金を伴う変更は無確認で流さない**」思想と、apply レイヤーで食い違っていた。

手動承認（`auto_apply = false`）にすれば plan を人が見てから apply できるが、**承認待ちの run に気づかないと apply が滞留する**（見逃しリスク）。

## Decision（決定）

HCP Terraform workspace `toy-box` を次の運用にする。

- **`auto_apply = false`（手動承認）**: main マージ後の post-merge run は plan で止まる。apply は人が HCP UI で plan（add/change/destroy）を確認して **Confirm & Apply** する。
- **HCP Notification を設定**: run の `needs_attention`（apply 承認待ち）/ `errored` / `completed` を **email** で通知し、承認待ちの見逃しを防ぐ。

設定は当面 **HCP UI で手動**で行う（workspace Settings → General の Apply Method、および Settings → Notifications）。IaC 化（`tfe` provider で workspace 設定を宣言）は、workspace が自身を管理する chicken-and-egg を整理してから将来検討する。

## Consequences（結果・影響）

- **不可逆・課金を伴う apply を、人が plan を確認してから流す**ようになった。Identity Platform config のような削除不可リソースの誤作成・破壊を apply 直前の人手ゲートで防ぐ。[ADR-0036](0036-gcp-operation-guardrails.md) の思想が plan → apply の全域で一貫する。
- **通知で承認待ちを見逃さない**（email の `needs_attention`）。手動承認の急所（滞留）を補う。
- 反面、apply に手動ステップ（承認待ち）が挟まり、マージから反映までひと手間増える。緊急時の apply も承認を待つ。
- workspace 設定を **HCP UI で手設定**したため、その状態は Terraform state / リポジトリの外にある（本 ADR が唯一の記録）。再現性のため将来 `tfe` provider での IaC 化を検討する（その際は本 ADR を追補または新 ADR で更新する）。
- 承認・通知・plan 確認の実務は [ADR-0034](0034-adopt-tfctl-cli.md)（tfctl）と `.claude/skills/hcp-run-inspect` と併せて辿る。
