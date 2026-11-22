# 環境を消しても残すもの（docs/12「作成と削除」）。費用はほぼ $0。
# - ECR：環境を作る前にイメージを push しておけるよう、こちらに置く
# - SNS：アラームの通知先。メールの購読確認を最初の 1 回だけで済ませるため
# - Budgets：消し忘れに気づくための予算アラート

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.73"
    }
  }

  # 状態ファイルのバケットは最初に AWS CLI で作る（infra/README.md）
  backend "s3" {
    bucket       = "timeline-sns-tfstate-579225707926"
    key          = "persistent/terraform.tfstate"
    region       = "ap-northeast-1"
    use_lockfile = true
  }
}

# 認証は環境変数で渡す。手元では AWS_PROFILE=trello-app（infra/README.md）、GitHub Actions では OIDC
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project = "timeline-sns"
      Stack   = "persistent"
    }
  }
}

data "aws_caller_identity" "current" {}

# バックエンドのコンテナイメージ。タグは Git のコミット ID
resource "aws_ecr_repository" "backend" {
  name                 = "timeline-sns-backend"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 古いイメージは消す（保存料金を増やさない）
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "最新 3 個だけ残す"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 3
      }
      action = { type = "expire" }
    }]
  })
}

# アラームの通知先（docs/10）。購読したら、届いた確認メールのリンクを押す
resource "aws_sns_topic" "alarms" {
  name = "timeline-sns-alarms"
}

resource "aws_sns_topic_subscription" "alarms_email" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# 料金のアラート。実績が予算の 80%・100% を超えたとき、予測が 100% を超えそうなときにメールする
resource "aws_budgets_budget" "monthly" {
  name         = "timeline-sns-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "notification" {
    for_each = [
      { threshold = 80, type = "ACTUAL" },
      { threshold = 100, type = "ACTUAL" },
      { threshold = 100, type = "FORECASTED" },
    ]
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value.threshold
      threshold_type             = "PERCENTAGE"
      notification_type          = notification.value.type
      subscriber_email_addresses = [var.alert_email]
    }
  }
}
