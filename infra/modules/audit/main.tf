# 監査アラートの通知先。宛先は公開リポジトリに直書きせず HCP workspace の変数で供給する
# （billing_account_id / prisma_* と同じ扱い）。
resource "google_monitoring_notification_channel" "audit" {
  display_name = "Audit alert"
  type         = "email"

  labels = {
    email_address = var.alert_email
  }
}

# 正規ルート（GitHub Actions の deployer@ / HCP Terraform run の tfc-service-account@）を
# 外れた変更を事後に検知する。ADR-0036 の「変更は CI/HCP に寄せる」を破ったときに鳴る。
#
# 記録そのものは Admin Activity ログが常時・無料で担っており（_Required バケットに 400 日、
# 保持は locked で変更不可）、呼び出し元も serviceAccountDelegationInfo から復元できる。
# 足りないのは気づく手段だけなので、ここでは検知だけを足す（ADR-0078）。
resource "google_monitoring_alert_policy" "human_admin_activity" {
  display_name = "Admin Activity by non-service-account principal"
  combiner     = "OR"
  severity     = "WARNING"

  conditions {
    display_name = "Admin Activity performed by a human principal"

    # ログベースメトリクス + 閾値ではなく log-based alert（condition_matched_log）にしている。
    # メトリクス経由は集計の遅延が乗るうえ、閾値の意味を持たない「1 件でも出たら異常」だから。
    condition_matched_log {
      # principal を列挙せず「サービスアカウントではない」の否定形で書く。SA が増えても追随不要で、
      # 列挙漏れが検知漏れに化けない。principalEmail:* は principal を持たないエントリ
      # （実測で 400 日に 1 件ある）が NOT を素通りして誤検知になるのを防ぐ。
      #
      # HCL の heredoc はバックスラッシュを解釈しないため、正規表現の \. をそのまま書ける
      # （quoted string だと不正なエスケープになる）。
      filter = <<-EOT
        log_id("cloudaudit.googleapis.com/activity")
        AND protoPayload.authenticationInfo.principalEmail:*
        AND NOT protoPayload.authenticationInfo.principalEmail=~"\.gserviceaccount\.com$"
      EOT
    }
  }

  # LogMatch 条件では notification_rate_limit が必須（未指定だと API に拒否される）。
  # Console 操作は 1 回の作業で複数の Admin Activity を生むため、まとめて 1 通にする。
  alert_strategy {
    notification_rate_limit {
      period = "300s"
    }
  }

  notification_channels = [google_monitoring_notification_channel.audit.id]

  documentation {
    subject   = "GCP: 正規ルート外の変更操作を検知しました"
    mime_type = "text/markdown"
    content   = <<-EOT
      Admin Activity ログに、サービスアカウント以外（人間）の principal による操作が記録されました。

      正規の変更ルートは GitHub Actions（deployer@）と HCP Terraform run（tfc-service-account@）で、
      どちらも principal はサービスアカウントになります。この通知が出たということは、次のどちらかです。

      - owner へ昇格してローカルから直接変更した（CLOUDSDK_AUTH_IMPERSONATE_SERVICE_ACCOUNT を外した）
      - Google Cloud Console から操作した

      意図した作業なら対応は不要です。心当たりがなければ、次で「誰が・何を」を確認してください。

          gcloud logging read 'log_id("cloudaudit.googleapis.com/activity")
            AND protoPayload.authenticationInfo.principalEmail:*
            AND NOT protoPayload.authenticationInfo.principalEmail=~"\.gserviceaccount\.com$"' \
            --project=ptiringo-toy-box --limit=10 --freshness=1d \
            --format='value(protoPayload.authenticationInfo.principalEmail,protoPayload.methodName,protoPayload.resourceName)'
    EOT
  }
}
