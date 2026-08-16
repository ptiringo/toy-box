# 予算の対象プロジェクト。budget_filter は projects/<project_number> 形式を要求するため、
# ID から番号を引く（番号をハードコードしない）。
data "google_project" "target" {
  project_id = var.project_id
}

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

  # 50% / 90% で予兆を掴む
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

  # 「今月このままだと超える」を月末を待たずに知る
  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }
}
