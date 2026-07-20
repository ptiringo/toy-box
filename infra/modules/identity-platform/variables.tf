variable "project_id" {
  description = "Identity Platform を有効化する GCP プロジェクト ID"
  type        = string
}

variable "authorized_domains" {
  description = "OAuth redirect を許可するドメイン。ローカル E2E で localhost:5173 から認証するため localhost を含める"
  type        = list(string)
  default = [
    "localhost",
    "ptiringo-toy-box.firebaseapp.com",
    "ptiringo-toy-box.web.app",
  ]
}
