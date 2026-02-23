resource "google_project_service" "project" {
  for_each = toset([
    "artifactregistry",
    "iam",
    "iamcredentials",
    "orgpolicy",
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
