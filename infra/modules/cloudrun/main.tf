# Cloud Run実行用サービスアカウント（最小権限の原則）
resource "google_service_account" "api_runner" {
  account_id   = "api-runner"
  display_name = "API Runner"
  description  = "Service account for running API on Cloud Run"
}

# DeployerがCloud Runデプロイ時にapi-runner SAを指定できるようにする権限
resource "google_service_account_iam_member" "deployer_act_as_api_runner" {
  service_account_id = google_service_account.api_runner.name
  role               = "roles/iam.serviceAccountUser"
  member             = var.deployer_member
}
