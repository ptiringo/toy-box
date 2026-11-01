# 本番の停止に気づくための uptime check（ADR-0089）。
#
# 間隔は API が許す最長の 900s にしている。確認のたびに Cloud Run の起動と DB への問い合わせが
# 発生しうるので、短くするほど費用と Prisma の operations を消費する。
resource "google_monitoring_uptime_check_config" "api_health" {
  display_name = "API health"
  period       = "900s"
  # min-instances=0 のためコールドスタートを踏むことがある。誤報を避けるため上限の 60s にする。
  timeout = "60s"

  # 未指定だと全リージョンから確認して回数が増える。API が要求する最小の 3 地点に絞る。
  selected_regions = ["ASIA_PACIFIC", "EUROPE", "USA_OREGON"]

  # サービスエージェント認証は uptime_url では API に拒否されるので、Cloud Run を直接の対象にする。
  monitored_resource {
    type = "cloud_run_revision"
    labels = {
      project_id   = var.project_id
      service_name = var.service_name
      location     = var.location
    }
  }

  http_check {
    path    = "/actuator/health"
    port    = 443
    use_ssl = true

    # サービスは --no-allow-unauthenticated なので、Monitoring のサービスエージェントの ID トークンで呼ぶ。
    # 呼び出しに要る run.routes.invoke は、このエージェントのロール
    # （roles/monitoring.notificationServiceAgent）に含まれるので、ここでは付与しない。
    service_agent_authentication {
      type = "OIDC_TOKEN"
    }
  }
}

resource "google_monitoring_alert_policy" "api_down" {
  display_name = "API health check failed"
  combiner     = "OR"
  severity     = "CRITICAL"

  conditions {
    display_name = "API health check failed from more than one location"

    condition_threshold {
      filter = join(" AND ", [
        "metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\"",
        "resource.type=\"cloud_run_revision\"",
        "metric.label.check_id=\"${google_monitoring_uptime_check_config.api_health.uptime_check_id}\"",
      ])

      # 整列幅は確認間隔より長くする。短いと確認のない窓ができて判定が途切れる。
      aggregations {
        alignment_period     = "1200s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.*"]
      }

      # 1 地点だけの失敗は通信の揺らぎとみなし、2 地点以上の失敗で通知する。
      comparison      = "COMPARISON_GT"
      threshold_value = 1
      duration        = "60s"

      trigger {
        count = 1
      }
    }
  }

  notification_channels = [var.notification_channel_id]

  documentation {
    subject   = "本番: API のヘルスチェックが失敗しています"
    mime_type = "text/markdown"
    content   = <<-EOT
      `/actuator/health` の uptime check が複数の地点で失敗しました。

      まず Cloud Run のログで、起動失敗か DB の接続エラーかを確認してください。DB のエラーなら、
      Prisma の operations が枠を超えて読み書きを拒否されている可能性があります（ADR-0074）。

          gcloud logging read 'resource.type="cloud_run_revision" AND severity>=ERROR' \
            --project=ptiringo-toy-box --limit=20 --freshness=1h
    EOT
  }
}
