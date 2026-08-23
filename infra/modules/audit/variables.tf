variable "alert_email" {
  description = "監査アラートの通知先メールアドレス（機微。HCP workspace の sensitive 変数で供給する）"
  type        = string
  sensitive   = true
}
