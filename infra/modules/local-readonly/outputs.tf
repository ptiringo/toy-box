output "service_account_email" {
  description = "ローカル read-only SA のメールアドレス（impersonation 先）"
  value       = google_service_account.local_readonly.email
}
