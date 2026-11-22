variable "aws_region" {
  description = "AWS のリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "alert_email" {
  description = "アラームと予算アラートの通知先メールアドレス"
  type        = string
}

variable "monthly_budget_usd" {
  description = "月の予算（USD）。80%・100% でメールが届く"
  type        = number
  default     = 5
}
