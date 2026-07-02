provider "google" {
  project = "ptiringo-toy-box"
  region  = "asia-northeast1"
  zone    = "asia-northeast1-a"
}

# Prisma Postgres の管理 API 認証。トークンは HCP workspace の sensitive 変数で供給する
# （Prisma Console で発行/ローテーション。値はリポジトリに置かない）。
provider "prisma-postgres" {
  service_token = var.prisma_service_token
}
