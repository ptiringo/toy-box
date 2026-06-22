# 0017. Terraform 品質ゲートに tflint と Trivy を採用し、policy-as-code（OPA）は当面見送る

- Status: Accepted
- Date: 2026-06-22
- Deciders: ptiringo

## Context（背景・課題）

`infra/`（GCP の Cloud Run + Artifact Registry + Workload Identity Federation + IAM の小規模構成、HCP Terraform バックエンド）の Terraform チェックは、現状 `terraform fmt -check` と `terraform validate`（lefthook の pre-commit と `terraform-check.yml` CI）のみ。validate は構文・内部整合まで、fmt はフォーマッタであり、次の層が欠けていた:

- **Lint**: 未使用宣言・非推奨構文・命名規則・provider 固有のありうるエラー
- **セキュリティ misconfig スキャン**: IAM 過剰権限・公開設定など既知のセキュリティ設定ミス（gitleaks/zizmor/CodeQL は稼働しているが IaC misconfig は未カバー）
- **policy-as-code**: 既製ルールで表せない組織独自のガバナンスルール

tflint・Trivy・OPA の採用可否を検討するにあたり、各ツールの2026年時点の状況を一次情報で調査した。判明した主要事実:

- **tflint**: アクティブ（v0.63.1）。mise の aqua backend で管理可。GCP ruleset（`tflint-ruleset-google`）あり。言語バンドルルールは `tflint --init` 不要だが、google ruleset はプラグイン取得が必要で CI に `GITHUB_TOKEN` + キャッシュが要る。
- **tfsec は非推奨**: AquaSecurity が Trivy に統合し、公式が `trivy config` への移行を案内（tfsec-to-trivy-migration-guide）。新規採用する理由はない。
- **Trivy**: アクティブ（v0.71.2）、Apache-2.0、無アカウント・ローカル完結。`trivy config` で Terraform/GCP の misconfig をスキャンし、SARIF を出力できる。mise の aqua backend で管理可。GCP は iam/kms/sql/storage 等8サービス対応。
- **Checkov**（次点）: GCP の Terraform ルールが129件と Trivy より広いが、出力がノイジーで小規模 infra には抑制管理が割に合いにくい。
- **OPA/conftest**: policy-as-code は組織独自ルールを汎用言語（Rego）で書く層。本質コストは「Rego を自前で書いて保守すること」。
- **HCP ネイティブの OPA policy set は実行に Premium edition（有償）が必要**（公式 manage-policy-sets の記述）。Free は policy set 1つ・最大5ポリシー止まり、VCS 連携も Standard 以上。

検討した代替と却下理由（セキュリティスキャナ層）: tfsec（非推奨・Trivy 統合済み）、Terrascan（2025-11 アーカイブ）、KICS（無料・活発だが GCP で Trivy/Checkov に対する明確な優位が薄い）、Snyk IaC（要アカウント・phone-home・月300回クォータ。オフライン/無アカウント方針と相性が悪い）。

## Decision（決定）

Terraform の品質ゲートを **3層**で捉え、層ごとに次を採用する:

1. **Lint 層 → tflint を採用**（[#377](https://github.com/ptiringo/toy-box/issues/377)）。段階導入とし、まず Terraform 言語バンドルの `preset = "recommended"` のみ（`--init` 不要・低コスト）。効果を見て GCP ruleset を追加し、その時点で CI に `GITHUB_TOKEN` + プラグインキャッシュ、lefthook に init スキップを足す。mise（aqua backend）で管理。
2. **セキュリティ misconfig 層 → Trivy（`trivy config`）を採用**（[#378](https://github.com/ptiringo/toy-box/issues/378)）。**tfsec は採らない**（非推奨・Trivy 統合済み）。SARIF を既存の `codeql-action/upload-sarif` 経路（`security-events: write` 済み）で Code Scanning にアップロードする。mise（aqua backend）で管理。Checkov は次点とし、ルール厳格さが要るときの切替候補として保持する。
3. **policy-as-code 層 → 当面見送る**（[#379](https://github.com/ptiringo/toy-box/issues/379) に保留として記録）。組織独自ガバナンス要件がまだ顕在化していないため。導入が必要になったら、(a) まず Trivy の Rego カスタムチェックで吸収できないか試し、(b) それでも独立基盤が要るなら **HCP ネイティブ OPA policy set（Premium 必須）ではなく CI で conftest** を選ぶ（無償・既存スタックに低摩擦・ポリシー資産がポータブル）。

3層は競合ではなく補完関係（lint × 既知 misconfig × 組織独自ポリシー）であり、置換ではなく併用する。いずれも HCP backend とは独立した静的解析であり、policy 強制を HCP 側に寄せる必然性はない。

## Consequences（結果・影響）

- validate/fmt では拾えなかった lint の穴と、未カバーだった IaC セキュリティ misconfig の両層を、既存のツール管理レール（mise の aqua backend + version pin、lefthook の glob フック、GitHub Actions の SARIF アップロード）にそのまま載せられる。新規ツールの導入コストは小さい。
- tfsec という非推奨ツールを避け、後継の Trivy に正面から乗ることで、将来のメンテ切れリスクを負わない。Trivy は1バイナリで misconfig に加えイメージ/SCA/secret も賄えるため、将来 `container-smoke-test.yml` 周りと統合する余地もある。
- tflint は本体（mise）と google ruleset（`.tflint.hcl` の `version`）を二箇所でピンすることになる。google ruleset を使う段では `--init` のプラグイン取得コスト（CI の `GITHUB_TOKEN` + キャッシュ、lefthook の init スキップ）を引き受ける。段階導入でこのコストを後ろ倒しにする。
- policy-as-code を見送ることで「Rego を書く相手がいないのに基盤だけ持つ」空回りを避ける。再評価のトリガー（組織独自ルールの顕在化）は #379 に明記し、その際は Trivy のカスタムチェック → CI conftest の順で検討する。HCP の OPA policy set は Premium 課金が前提のため、無償・小規模運用では選ばない。
- 実装は各 issue（#377 / #378）で行う。本 ADR は層構造とツール選定（特に「tfsec ではなく Trivy」「OPA は当面見送り」）の決定を記録する。
