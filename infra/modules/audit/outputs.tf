output "notification_channel_id" {
  description = "メールの通知チャネルの ID（uptime check のアラートでも使う）"
  value       = google_monitoring_notification_channel.audit.id
}
