variable "project_id" {
  description = "uptime check を置く GCP プロジェクトの ID"
  type        = string
}

variable "service_name" {
  description = "確認対象の Cloud Run サービス名"
  type        = string
}

variable "location" {
  description = "確認対象の Cloud Run サービスのリージョン"
  type        = string
}

variable "notification_channel_id" {
  description = "失敗を通知する通知チャネルの ID"
  type        = string
}
