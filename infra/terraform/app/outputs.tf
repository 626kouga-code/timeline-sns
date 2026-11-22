output "url" {
  description = "アプリの URL（CloudFront）"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "distribution_id" {
  description = "CloudFront のディストリビューション ID（キャッシュの無効化に使う）"
  value       = aws_cloudfront_distribution.main.id
}

output "web_bucket" {
  description = "フロントのビルドを置くバケット"
  value       = aws_s3_bucket.web.bucket
}

output "media_bucket" {
  value = aws_s3_bucket.media.bucket
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.backend.name
}

output "log_group" {
  value = aws_cloudwatch_log_group.backend.name
}
