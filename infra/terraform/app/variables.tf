variable "aws_region" {
  description = "AWS のリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "image_tag" {
  description = "動かすバックエンドのイメージのタグ（Git のコミット ID）。先に ECR へ push しておく"
  type        = string
}

variable "desired_count" {
  description = "動かすタスクの数"
  type        = number
  default     = 1
}
