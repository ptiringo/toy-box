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

# 高くつく操作を、principal によらず知らせる（ADR-0090 の検知 1）。
#
# 課金データは数時間遅れて届き、止められるのは Cloud Run の spend cap だけなので、乗っ取られた
# owner の権限で VM などを作られると気づくのが遅れる。上の human_admin_activity と違い SA も
# 対象にする。乗っ取られた経路が HCP の apply（tfc-service-account）でも鳴らすためで、正規の
# apply で API を有効にしたときも鳴る（過去 400 日で 20 件、下の rate limit でまとめると 6 回ほど。
# 2026-11-08 の実測）。
#
# 見るのは 3 つ:
#   - API の有効化。GKE や Cloud SQL など高くつくサービスの API は無効なので、使うにはまずここを通る
#   - Compute Engine の VM 作成。今は API が無効だが、有効にした後の作成も拾う（v1 / beta の両方）
#   - App Engine のバージョン作成。API が有効なまま残っているため
resource "google_monitoring_alert_policy" "costly_operation" {
  display_name = "Costly operation by any principal"
  combiner     = "OR"
  severity     = "WARNING"

  conditions {
    display_name = "API enablement or costly resource creation"

    condition_matched_log {
      filter = <<-EOT
        log_id("cloudaudit.googleapis.com/activity")
        AND (
          protoPayload.methodName=~"ServiceUsage\.(EnableService|BatchEnableServices)$"
          OR protoPayload.methodName=~"compute\.instances\.insert$"
          OR protoPayload.methodName="google.appengine.v1.Versions.CreateVersion"
        )
      EOT
    }
  }

  # 1 回の apply で API をまとめて有効にすると複数件出るため、まとめて 1 通にする。
  alert_strategy {
    notification_rate_limit {
      period = "300s"
    }
  }

  notification_channels = [google_monitoring_notification_channel.audit.id]

  documentation {
    subject   = "GCP: 高くつく操作（API の有効化・VM の作成など）を検知しました"
    mime_type = "text/markdown"
    content   = <<-EOT
      Admin Activity ログに、API の有効化か、VM・App Engine のバージョンの作成が記録されました。
      課金データは数時間遅れて届くので、請求に表れる前に知らせています（ADR-0090）。

      HCP Terraform の apply（tfc-service-account@）で API を有効にした直後なら、対応は不要です。
      心当たりがなければ、次で「誰が・何を」を確認してください。

          gcloud logging read 'log_id("cloudaudit.googleapis.com/activity")
            AND (protoPayload.methodName=~"ServiceUsage\.(EnableService|BatchEnableServices)$"
              OR protoPayload.methodName=~"compute\.instances\.insert$"
              OR protoPayload.methodName="google.appengine.v1.Versions.CreateVersion")' \
            --project=ptiringo-toy-box --limit=10 --freshness=1d \
            --format='value(protoPayload.authenticationInfo.principalEmail,protoPayload.methodName,protoPayload.resourceName)'

      SA が principal のときは、protoPayload.authenticationInfo.serviceAccountDelegationInfo で
      呼び出し元（HCP の workspace や GitHub Actions のリポジトリ）まで辿れます。
    EOT
  }
}

# データの書き換えの入口が開いたら知らせる（ADR-0090 の検知 2）。
#
# 書き換えに対する主な守りは、Cloud Run の IAM が空であること。入口が開く経路は 2 つある:
#   - IAM に allUsers / allAuthenticatedUsers を入れる（Cloud Run・プロジェクトのどちらでも
#     request.policy.bindings にポリシー全体が載るので、同じ条件で拾える）
#   - Cloud Run の invoker IAM チェックを外す。IAM を変えずにサービスの更新で公開できるため、
#     SetIamPolicy だけを見ていると素通りする（v1 は annotation、v2 はフィールド）
#
# メソッド名だけで絞らない。deployer@ はデプロイのたびに空のポリシーで
# google.cloud.run.v1.Services.SetIamPolicy を呼ぶ（過去 400 日で 200 件以上。ADR-0090 の実測）。
resource "google_monitoring_alert_policy" "public_access_granted" {
  display_name = "Public access granted"
  combiner     = "OR"
  severity     = "CRITICAL"

  conditions {
    display_name = "allUsers binding or invoker IAM check disabled"

    condition_matched_log {
      filter = <<-EOT
        log_id("cloudaudit.googleapis.com/activity")
        AND (
          (
            protoPayload.methodName=~"SetIamPolicy$"
            AND (
              protoPayload.request.policy.bindings.members:"allUsers"
              OR protoPayload.request.policy.bindings.members:"allAuthenticatedUsers"
            )
          )
          OR protoPayload.request.service.metadata.annotations."run.googleapis.com/invoker-iam-disabled"="true"
          OR protoPayload.request.service.invokerIamDisabled=true
        )
      EOT
    }
  }

  alert_strategy {
    notification_rate_limit {
      period = "300s"
    }
  }

  notification_channels = [google_monitoring_notification_channel.audit.id]

  documentation {
    subject   = "GCP: 誰でも呼べる状態にする変更を検知しました"
    mime_type = "text/markdown"
    content   = <<-EOT
      IAM に allUsers / allAuthenticatedUsers が入ったか、Cloud Run の invoker IAM チェックが外されました。
      本番 API は「Cloud Run の IAM が空」であることで外部からのデータの書き換えを防いでいるので、
      意図した公開でなければすぐに戻してください（ADR-0090）。

          gcloud logging read 'log_id("cloudaudit.googleapis.com/activity")
            AND (protoPayload.methodName=~"SetIamPolicy$"
              OR protoPayload.methodName:"Service")' \
            --project=ptiringo-toy-box --limit=10 --freshness=1d \
            --format='value(protoPayload.authenticationInfo.principalEmail,protoPayload.methodName,protoPayload.resourceName)'
    EOT
  }
}
