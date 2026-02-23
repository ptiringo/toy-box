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

  # WIF バインディングが org policy より先に作成されないようにする
  depends_on = [google_org_policy_policy.allowed_policy_members]
}

module "cloudrun" {
  source = "./modules/cloudrun"

  deployer_member = module.cicd.deployer_member
}

# プロジェクトレベルの組織ポリシー: WIF プールからの principalSet を許可
resource "google_org_policy_policy" "allowed_policy_members" {
  name   = "projects/ptiringo-toy-box/policies/iam.managed.allowedPolicyMembers"
  parent = "projects/ptiringo-toy-box"

  spec {
    inherit_from_parent = true

    rules {
      enforce = "TRUE"
      parameters = jsonencode({
        allowedMemberSubjects = []
        allowedPrincipalSets = [
          "principalSet://iam.googleapis.com/projects/865764667244/locations/global/workloadIdentityPools/github-actions-pool-id/*"
        ]
      })
    }
  }
}
