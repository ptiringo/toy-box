resource "google_project_service" "project" {
  for_each = toset([
    "artifactregistry",
    "iam",
    "iamcredentials",
    "run",
  ])
  service = "${each.key}.googleapis.com"
}

resource "google_artifact_registry_repository" "api" {
  format        = "DOCKER"
  repository_id = "api"
  description   = "API repository"
  location      = "asia-northeast1"
}

module "cicd" {
  source = "./modules/cicd"

  project_id                      = "ptiringo-toy-box"
  wif_project_number              = var.wif_project_number
  github_repository               = "ptiringo/toy-box"
  artifact_registry_repository_id = google_artifact_registry_repository.api.id
}

module "cloudrun" {
  source = "./modules/cloudrun"

  deployer_member = module.cicd.deployer_member
}

# State 移行用 moved ブロック（apply 後に削除可能）

moved {
  from = google_service_account.deployer
  to   = module.cicd.google_service_account.deployer
}

moved {
  from = google_artifact_registry_repository_iam_member.deployer_api_writer
  to   = module.cicd.google_artifact_registry_repository_iam_member.deployer_api_writer
}
