resource "google_service_account" "deployer" {
  account_id   = "deployer"
  display_name = "Deployer"
  description  = "Service account for deploying applications"
}

# 既存WIF Pool（pt-workload-identity）からDeployer SAへのバインディング
# principal:// (単数) で sub claim と完全一致させることで、
# 「対象リポジトリ AND var.deploy_environment で指定した environment（既定: production）」の
# AND 条件で制限している。
# environment 側の保護ルール（main ブランチ限定、承認者必須など）は GitHub UI で設定。
resource "google_service_account_iam_member" "deployer_workload_identity" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/projects/${var.wif_project_number}/locations/global/workloadIdentityPools/github/subject/repo:${var.github_repository}:environment:${var.deploy_environment}"
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
