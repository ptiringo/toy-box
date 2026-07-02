resource "google_project_service" "project" {
  for_each = toset([
    "artifactregistry",
    "iam",
    "iamcredentials",
    "orgpolicy",
    "run",
    "secretmanager",
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

module "local_readonly" {
  source = "./modules/local-readonly"

  project_id    = "ptiringo-toy-box"
  impersonators = var.local_readonly_impersonators
}

# 本番 DB（Prisma Postgres・東京）と、その接続情報を格納する Secret Manager。
# secretmanager API 有効化に依存させ、初回 apply の順序レースを避ける。
module "prisma_postgres" {
  source = "./modules/prisma-postgres"

  api_runner_email = module.cloudrun.api_runner_email
  project_name     = var.prisma_project_name

  depends_on = [google_project_service.project]
}
