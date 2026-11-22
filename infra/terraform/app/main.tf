# 動作確認のたびに作り、終わったら terraform destroy で全部消す環境（docs/12）。
# 消さずに残すもの（ECR・SNS・Budgets）は ../persistent にある。

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.73"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }

  backend "s3" {
    bucket       = "timeline-sns-tfstate-579225707926"
    key          = "app/terraform.tfstate"
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
      Stack   = "app"
    }
  }
}

# 残しておく側（ECR・アラームの通知先）の出力を読む
data "terraform_remote_state" "persistent" {
  backend = "s3"
  config = {
    bucket = "timeline-sns-tfstate-579225707926"
    key    = "persistent/terraform.tfstate"
    region = "ap-northeast-1"
  }
}

data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name       = "timeline-sns"
  azs        = slice(data.aws_availability_zones.available.names, 0, 2)
  account_id = data.aws_caller_identity.current.account_id
  persistent = data.terraform_remote_state.persistent.outputs
}
