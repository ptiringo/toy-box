variable "api_runner_email" {
  description = "datasource secret への参照を許可する api-runner SA のメールアドレス"
  type        = string
}

variable "project_name" {
  description = "Prisma Postgres プロジェクト名（データベースの上位コンテナ）。機微情報として扱い、既定値は置かず HCP の sensitive 変数で供給する"
  type        = string
  sensitive   = true
}

variable "database_name" {
  description = "Prisma Postgres データベース名"
  type        = string
  default     = "production"
}

variable "region" {
  description = "Prisma Postgres のデプロイ先リージョン（東京）。Prisma のリージョン表記であり GCP の asia-northeast1 とは別体系"
  type        = string
  default     = "ap-northeast-1"
}
