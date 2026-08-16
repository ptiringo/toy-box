# 予算の対象プロジェクト。budget_filter は projects/<project_number> 形式を要求するため、
# provider に設定された project を対象に番号を引く（番号をハードコードしない）。
data "google_project" "target" {}

# プロジェクト全体の月次予算アラート。
# 到達しても課金は止まらない（検知のみ）。止めるのは Cloud Run の spend cap（Console 手設定・ADR-0074）。
#
# 通知先は明示しない。all_updates_rule を書かないことで、請求先アカウントの管理者宛に
# 既定のメール通知が飛ぶ（宛先をコードに載せずに済む）。
resource "google_billing_budget" "project_total" {
  billing_account = var.billing_account_id
  display_name    = "toy-box project total"

  budget_filter {
    projects        = ["projects/${data.google_project.target.number}"]
    calendar_period = "MONTH"
  }

  amount {
    specified_amount {
      currency_code = "JPY"
      units         = var.monthly_amount_jpy
    }
  }

  # 50% / 90%（実績）。Cloud Run は spend cap（¥1,000・ADR-0074）が先に切れるため、
  # Cloud Run 暴走時はここに届く前に利用停止する。実質的にここが見張るのは
  # spend cap 対象外のサービス（Artifact Registry 等）の緩やかな増加。
  threshold_rules {
    threshold_percent = 0.5
  }

  threshold_rules {
    threshold_percent = 0.9
  }

  # 100% 到達
  threshold_rules {
    threshold_percent = 1.0
  }

  # 「今月このままだと超える」を月末を待たずに知る。
  # Cloud Run 暴走の予兆はこちら（予測）が担う（実績閾値は spend cap に先を越される）。
  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }
}
