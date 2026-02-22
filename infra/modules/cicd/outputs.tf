output "deployer_name" {
  description = "Deployer SA の name（IAM バインディング用）"
  value       = google_service_account.deployer.name
}

output "deployer_member" {
  description = "Deployer SA の member（プロジェクトレベル IAM 用）"
  value       = google_service_account.deployer.member
}
