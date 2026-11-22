output "ecr_repository_url" {
  description = "バックエンドのイメージの push 先"
  value       = aws_ecr_repository.backend.repository_url
}

output "alarm_topic_arn" {
  description = "アラームの通知先（app の構成が参照する）"
  value       = aws_sns_topic.alarms.arn
}

output "account_id" {
  value = data.aws_caller_identity.current.account_id
}
