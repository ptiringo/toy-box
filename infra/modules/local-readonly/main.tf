# ローカル作業（Claude Code 含む）用の read-only サービスアカウント。
# 利用者は impersonation で使い、変更系の権限は持たせない（最小権限の原則）。
resource "google_service_account" "local_readonly" {
  account_id   = "local-readonly"
  display_name = "Local Read-Only"
  description  = "Service account for local read-only access (impersonated by developers)"
}

# project 全体の閲覧権限のみ（viewer）。変更権限は付与しない。
resource "google_project_iam_member" "local_readonly_viewer" {
  project = var.project_id
  role    = "roles/viewer"
  member  = google_service_account.local_readonly.member
}

# 指定した開発者が local-readonly SA を impersonate（短命トークン発行）できるようにする。
resource "google_service_account_iam_member" "local_readonly_token_creator" {
  for_each           = toset(var.impersonators)
  service_account_id = google_service_account.local_readonly.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = each.value
}
