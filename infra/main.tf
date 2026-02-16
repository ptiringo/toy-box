resource "google_project_service" "project" {
  for_each = toset([
    "artifactregistry",
    "iam",
    "iamcredentials",
  ])
  service = "${each.key}.googleapis.com"
}

resource "google_artifact_registry_repository" "api" {
  format        = "DOCKER"
  repository_id = "api"
  description   = "API repository"
  location      = "asia-northeast1"
}

resource "google_service_account" "deployer" {
  account_id   = "deployer"
  display_name = "Deployer"
  description  = "Service account for deploying applications"
}

# DeployerサービスアカウントにArtifact Registry(apiリポジトリ)への書き込み権限を付与
resource "google_artifact_registry_repository_iam_member" "deployer_api_writer" {
  project    = google_artifact_registry_repository.api.project
  location   = google_artifact_registry_repository.api.location
  repository = google_artifact_registry_repository.api.name
  role       = "roles/artifactregistry.writer"
  member     = google_service_account.deployer.member
}

