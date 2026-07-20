output "issuer_uri" {
  description = "バックエンド（OAuth2 リソースサーバ）が JWT 検証に使う issuer。GCP_PROJECT_ID から組み立てる値と一致する（ADR-0064）"
  value       = "https://securetoken.google.com/${var.project_id}"
}
