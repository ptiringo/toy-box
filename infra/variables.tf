variable "wif_project_number" {
  description = "Workload Identity Federation のプロジェクト番号"
  type        = string
}

variable "local_readonly_impersonators" {
  description = "local-readonly SA を impersonate できるメンバー（例: [\"user:foo@example.com\"]）。既定は空"
  type        = list(string)
  default     = []
}

variable "prisma_service_token" {
  description = "Prisma Postgres 管理 API のサービストークン（HCP workspace の sensitive 変数で供給）"
  type        = string
  sensitive   = true
}

variable "prisma_project_name" {
  description = "Prisma Postgres プロジェクト名（機微。HCP workspace の sensitive 変数で供給）"
  type        = string
  sensitive   = true
}
