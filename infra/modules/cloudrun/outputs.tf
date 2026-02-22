output "api_runner_email" {
  description = "api-runner SA のメールアドレス"
  value       = google_service_account.api_runner.email
}
