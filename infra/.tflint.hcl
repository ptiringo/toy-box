# tflint 設定（ADR-0017 / #377 フェーズ1）
#
# Terraform 言語バンドルルールの recommended preset のみを有効化する。
# 言語プラグインは tflint 本体に同梱されるため `tflint --init` は不要（プラグイン取得が要る
# GCP ruleset はフェーズ2 で追加し、その際 CI に GITHUB_TOKEN + キャッシュ、lefthook に
# init スキップを足す）。
#
# 有効になる主なルール: terraform_unused_declarations / terraform_required_providers /
# terraform_required_version / terraform_deprecated_* など。
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}
