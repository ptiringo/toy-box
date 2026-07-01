# Prisma Postgres 本番インスタンス（東京 ap-northeast-1・無料枠）。
# 本物の PostgreSQL v17 へ direct TCP（標準 pgwire / JDBC）で接続する（ADR-0044 / #451 Phase B で de-risk 済み）。
resource "prisma-postgres_project" "toy_box" {
  name = var.project_name
}

resource "prisma-postgres_database" "production" {
  project_id = prisma-postgres_project.toy_box.id
  name       = var.database_name
  region     = var.region
}

# Cloud Run へ注入する datasource 接続情報（SPRING_DATASOURCE_*）を Secret Manager に格納する。
# direct_host は Prisma 共有の安定ホスト、direct_user / direct_password は per-DB（再作成で変わる）。
# PG カタログのデータベース名は Prisma の表示名によらず postgres 固定（#451 Phase B の spike で確認）。
locals {
  datasource_secrets = {
    "spring-datasource-url"      = "jdbc:postgresql://${prisma-postgres_database.production.direct_host}:5432/postgres?sslmode=require"
    "spring-datasource-username" = prisma-postgres_database.production.direct_user
    "spring-datasource-password" = prisma-postgres_database.production.direct_password
  }
}

resource "google_secret_manager_secret" "datasource" {
  for_each = local.datasource_secrets

  secret_id = each.key

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "datasource" {
  for_each = local.datasource_secrets

  secret      = google_secret_manager_secret.datasource[each.key].id
  secret_data = each.value
}

# api-runner SA へ、この 3 secret のみ参照権限を付与する（最小権限）。
resource "google_secret_manager_secret_iam_member" "api_runner_accessor" {
  for_each = google_secret_manager_secret.datasource

  secret_id = each.value.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${var.api_runner_email}"
}
