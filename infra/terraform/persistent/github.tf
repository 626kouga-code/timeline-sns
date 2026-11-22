# GitHub Actions から AWS を操作するための設定（#93、docs/12「秘密情報と権限」）。
# OIDC で一時的な認証情報を受け取るので、長期のアクセスキーは作らない。

# GitHub が OIDC のトークンで名乗る名前（sub）の先頭。このリポジトリは「変わらない ID 付き」の形式
# （owner@ユーザーID/リポジトリ@リポジトリID）なので、名前を変えたり同じ名前のリポジトリを作られたりしても成りすませない。
# 確かめ方：gh api repos/626kouga-code/timeline-sns/actions/oidc/customization/sub の sub_claim_prefix
variable "github_subject_prefix" {
  description = "AWS を操作してよい GitHub のリポジトリの sub の先頭"
  type        = string
  default     = "repo:626kouga-code@318127215/timeline-sns@1396052103"
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

# このリポジトリの main ブランチで動くワークフローだけが引き受けられる（PR やほかのブランチからは使えない）
data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["${var.github_subject_prefix}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "timeline-sns-github-deploy"
  description          = "GitHub Actions (main branch) creates, deploys and destroys the app stack"
  assume_role_policy   = data.aws_iam_policy_document.github_assume.json
  max_session_duration = 7200
}

# Terraform で VPC・IAM ロール・RDS・CloudFront などを作って消すので、権限は管理者にする。
# その代わり、引き受けられるのを main ブランチのワークフローだけに絞っている（上の条件）
resource "aws_iam_role_policy_attachment" "github_deploy_admin" {
  role       = aws_iam_role.github_deploy.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

output "github_deploy_role_arn" {
  description = "ワークフローの role-to-assume に指定する"
  value       = aws_iam_role.github_deploy.arn
}
