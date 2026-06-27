variable "wif_project_number" {
  description = "Workload Identity Federation のプロジェクト番号"
  type        = string
}

variable "local_readonly_impersonators" {
  description = "local-readonly SA を impersonate できるメンバー（例: [\"user:foo@example.com\"]）。既定は空"
  type        = list(string)
  default     = []
}
