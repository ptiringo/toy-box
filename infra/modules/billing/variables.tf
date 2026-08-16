variable "billing_account_id" {
  description = "budget を作成する Cloud Billing アカウント ID（例: 012345-6789AB-CDEF01）"
  type        = string
}

variable "project_id" {
  description = "予算の対象とする GCP プロジェクト ID"
  type        = string
}

variable "monthly_amount_jpy" {
  description = "プロジェクト全体の月次予算額（円）。この額に対する割合で閾値アラートが飛ぶ"
  type        = string
  default     = "3000"
}
