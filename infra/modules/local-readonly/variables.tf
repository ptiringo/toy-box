variable "project_id" {
  description = "対象 GCP プロジェクト ID"
  type        = string
}

variable "impersonators" {
  description = "local-readonly SA を impersonate できるメンバー（例: user:foo@example.com）。空なら誰にも付与しない"
  type        = list(string)
  default     = []
}
