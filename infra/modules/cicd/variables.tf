variable "wif_project_number" {
  description = "WIF プロジェクト番号"
  type        = string
}

variable "github_repository" {
  description = "GitHub リポジトリ（例: ptiringo/toy-box）"
  type        = string
}

variable "artifact_registry_repository_id" {
  description = "Artifact Registry リポジトリの ID"
  type        = string
}

variable "project_id" {
  description = "GCP プロジェクト ID"
  type        = string
}
