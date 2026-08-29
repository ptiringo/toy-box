variable "billing_account_id" {
  description = "budget を作成する Cloud Billing アカウント ID（例: 012345-6789AB-CDEF01）"
  type        = string
}

variable "monthly_amount_jpy" {
  description = "プロジェクト全体の月次予算額（円）。この額に対する割合で閾値アラートが飛ぶ"
  type        = string
  default     = "3000"
}

# budget_filter は projects/<project_number> 形式を要求するため番号で受け取る（番号をハードコードしない）。
# data source をこのモジュール内に置くと、モジュールが depends_on を持つ都合で plan 時に読まれず
# apply へ延期され、plan のたびに update が出る（#834）。そのためルートで引いて渡す。
variable "project_number" {
  description = "予算の対象プロジェクトのプロジェクト番号（ルートの data.google_project から供給する）"
  type        = string
}
