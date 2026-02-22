resource "google_service_account" "deployer" {
  account_id   = "deployer"
  display_name = "Deployer"
  description  = "Service account for deploying applications"
}

# 既存WIF Pool（workload-identity-project）からDeployer SAへのバインディング
resource "google_service_account_iam_member" "deployer_workload_identity" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/projects/${var.wif_project_number}/locations/global/workloadIdentityPools/github-actions-pool-id/attribute.repository/${var.github_repository}"
}

# DeployerサービスアカウントにArtifact Registry(apiリポジトリ)への書き込み権限を付与
resource "google_artifact_registry_repository_iam_member" "deployer_api_writer" {
  repository = var.artifact_registry_repository_id
  role       = "roles/artifactregistry.writer"
  member     = google_service_account.deployer.member
}

# Cloud Run開発者権限（サービスの更新・デプロイ）
resource "google_project_iam_member" "deployer_run_developer" {
  project = var.project_id
  role    = "roles/run.developer"
  member  = google_service_account.deployer.member
}
