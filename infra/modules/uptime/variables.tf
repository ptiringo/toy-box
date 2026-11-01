variable "project_id" {
  description = "uptime check を置く GCP プロジェクトの ID"
  type        = string
}

variable "host" {
  description = "確認対象の Cloud Run サービスのホスト名（スキームなし）"
  type        = string
}

variable "notification_channel_id" {
  description = "失敗を通知する通知チャネルの ID"
  type        = string
}
