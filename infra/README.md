# AWS 環境（Terraform）

AWS の構成と考え方は [docs/12_aws-architecture.md](../docs/12_aws-architecture.md)。ここは、手元のコマンドで作る・デプロイする・消す手順をまとめる（#93 で GitHub Actions のワークフローにする）。

| フォルダ | 中身 | 消すか |
| --- | --- | --- |
| [terraform/persistent](terraform/persistent) | ECR（イメージの置き場）、アラームの通知先（SNS）、予算アラート（Budgets） | 消さない（費用はほぼ $0） |
| [terraform/app](terraform/app) | VPC・NAT Gateway・ALB・ECS・RDS・S3・CloudFront・アラームなど | 動作確認が終わったら消す（約 $0.15/時） |

状態ファイルは S3 のバケット `timeline-sns-tfstate-579225707926` に置く（S3 のロックで同時実行を防ぐ）。

## 準備（最初の 1 回だけ）

- Terraform 1.10 以上、AWS CLI v2、Docker（buildx）
- 認証は AWS CLI のプロファイル `trello-app` を使う。以下のコマンドは、先に環境変数で指定しておく

    ```bash
    export AWS_PROFILE=trello-app        # PowerShell: $env:AWS_PROFILE = "trello-app"
    ```

- 状態ファイルのバケット（作成済み。作り直すときだけ）

    ```bash
    aws s3api create-bucket --bucket timeline-sns-tfstate-579225707926 --region ap-northeast-1 \
      --create-bucket-configuration LocationConstraint=ap-northeast-1
    aws s3api put-bucket-versioning --bucket timeline-sns-tfstate-579225707926 --versioning-configuration Status=Enabled
    ```

- 残しておく側を作る。`terraform/persistent/terraform.tfvars` に通知先を書く（`terraform.tfvars.example` を写す。リポジトリには入れない）

    ```bash
    terraform -chdir=infra/terraform/persistent init
    terraform -chdir=infra/terraform/persistent apply
    ```

    届いた「AWS Notification - Subscription Confirmation」のメールのリンクを押す（押さないとアラームのメールが届かない）

## 作る → デプロイ（20〜30 分）

```bash
TAG=$(git rev-parse --short=12 HEAD)
ECR=579225707926.dkr.ecr.ap-northeast-1.amazonaws.com/timeline-sns-backend

# 1. バックエンドのイメージ（ARM）を ECR に置く
aws ecr get-login-password --region ap-northeast-1 | docker login --username AWS --password-stdin ${ECR%%/*}
docker buildx build --platform linux/arm64 -t $ECR:$TAG --push backend

# 2. 環境を作る（RDS・CloudFront の作成に時間がかかる）
terraform -chdir=infra/terraform/app init
terraform -chdir=infra/terraform/app apply -var image_tag=$TAG

# 3. フロントを置く。assets/ はファイル名が内容ごとに変わるので長くキャッシュし、index.html はキャッシュさせない
(cd frontend && npm ci && npm run build)
WEB=$(terraform -chdir=infra/terraform/app output -raw web_bucket)
aws s3 sync frontend/dist/assets s3://$WEB/assets --delete --cache-control "public, max-age=31536000, immutable"
aws s3 sync frontend/dist s3://$WEB --delete --exclude "assets/*" --cache-control "no-cache"

terraform -chdir=infra/terraform/app output url   # https://xxxx.cloudfront.net
```

- 作ったあとにフロントだけ直したときは、3 をやり直してから `aws cloudfront create-invalidation --distribution-id $(terraform -chdir=infra/terraform/app output -raw distribution_id) --paths "/index.html"`
- バックエンドを直したときは、新しいコミットで 1 と `apply -var image_tag=<新しいタグ>` をやり直す（ECS が新しいタスクに入れ替える）

## 動作確認

- 出力された URL をブラウザで開き、登録・ログイン・画像付き投稿・いいね・コメント・フォロー・通知を試す。画面のパスでリロードしても画面が出る
- 画像の URL が `/media/...` で、表示される
- OGP：`curl -A Twitterbot https://<URL>/posts/<投稿のID>` で `og:title` などの入った HTML が返る（ブラウザで開くと普通の画面）
- ログ：`aws logs tail /timeline-sns/backend --follow`

## 全部消す（10〜20 分）

```bash
terraform -chdir=infra/terraform/app destroy -var image_tag=$TAG
```

- **DB の中身と投稿画像も消える**。残るのは persistent（ECR・SNS・Budgets）と状態ファイルのバケットだけ
- 消し忘れると 1 日 約 $3.7 かかり続ける。予算アラート（月 $5）のメールで気づける
- 失敗したらもう一度実行する。それでも残ったものは AWS のコンソールで確かめて消す
