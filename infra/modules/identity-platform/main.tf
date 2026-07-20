# GCP Identity Platform の設定（認証は Identity Platform に委譲・ADR-0064 / #612）。
# email/password サインインを有効化し、ローカル E2E 用に localhost を authorized_domains へ入れる。
# config は API 上一度作ると削除できない（個別プロバイダの disable のみ）。billing 有効なプロジェクトが前提。
resource "google_identity_platform_config" "default" {
  project = var.project_id

  sign_in {
    email {
      enabled           = true
      password_required = true
    }
  }

  authorized_domains = var.authorized_domains

  # 注: この resource は deletion_policy 引数を持たない（provider の magic-modules 定義で
  # exclude_delete: true。生成される Delete 実装は GCP API を呼ばず state から外すだけの
  # no-op に固定されているため、そもそも実削除が起きない＝ABANDON 相当が常時の挙動）。
  # google 7.39.0 / 7.40.0（GA・beta とも）で deletion_policy を指定すると
  # `terraform validate` が Unsupported argument で落ちることを実機確認済み。
  # registry docs（terraform.io の resource ページ）には deletion_policy が記載されているが、
  # これは magic-modules の doc テンプレートが持つ boilerplate であり、実際に生成された
  # provider の resource schema（resource_identity_platform_config.go の Schema map）には
  # 該当 field が存在しない。docs と実 schema が食い違うケースなので、docs だけを見て
  # 「引数を足すべき」と早合点しないこと（実測で Unsupported argument になることを確認済み）。
}
