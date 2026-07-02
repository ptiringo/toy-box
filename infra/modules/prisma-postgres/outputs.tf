# Phase D（cutover）で Cloud Run に --set-secrets 注入する際に参照する secret ID マップ。
# 値（接続情報そのもの）は sensitive なので出力しない。
output "datasource_secret_ids" {
  description = "SPRING_DATASOURCE_* を格納した Secret Manager secret の ID マップ"
  value       = { for key, secret in google_secret_manager_secret.datasource : key => secret.secret_id }
}

output "database_status" {
  description = "Prisma Postgres データベースの現在の状態"
  value       = prisma-postgres_database.production.status
}
