variable "api_runner_email" {
  description = "datasource secret への参照を許可する api-runner SA のメールアドレス"
  type        = string
}

variable "project_name" {
  description = "Prisma Postgres プロジェクト名（データベースの上位コンテナ）"
  type        = string
  default     = "toy-box"
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
