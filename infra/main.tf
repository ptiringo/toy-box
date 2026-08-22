resource "google_project_service" "project" {
  for_each = toset([
    "artifactregistry",
    "billingbudgets",
    "iam",
    "iamcredentials",
    "identitytoolkit",
    "monitoring",
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

# Identity Platform（認証委譲先。email/password + authorized_domains）。ADR-0064 / #612。
# identitytoolkit API 有効化に依存させ、初回 apply の順序レースを避ける。
module "identity_platform" {
  source = "./modules/identity-platform"

  project_id = "ptiringo-toy-box"

  depends_on = [google_project_service.project]
}

# 課金の見張り（プロジェクト全体の月次予算アラート）。#700 / ADR-0074。
# 止めるのは Cloud Run の spend cap（Console 手設定）で、ここは検知のみ。
# billingbudgets API 有効化に依存させ、初回 apply の順序レースを避ける。
module "billing" {
  source = "./modules/billing"

  billing_account_id = var.billing_account_id
  monthly_amount_jpy = "3000"

  depends_on = [google_project_service.project]
}

# 監査の検知（正規ルートを外れた変更操作のアラート）。#473 / ADR-0078。
# 記録は Admin Activity ログが常時担うので、ここで足すのは「気づく手段」だけ。
# monitoring API 有効化に依存させ、初回 apply の順序レースを避ける。
module "audit" {
  source = "./modules/audit"

  alert_email = var.audit_alert_email

  depends_on = [google_project_service.project]
}
