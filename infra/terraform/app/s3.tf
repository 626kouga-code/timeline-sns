# フロント（ビルドした dist/）と画像のバケット。どちらも非公開で、CloudFront（OAC）からだけ読める。
# 消すときは中身ごと消す（force_destroy）

resource "aws_s3_bucket" "web" {
  bucket        = "${local.name}-web-${local.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket" "media" {
  bucket        = "${local.name}-media-${local.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket                  = aws_s3_bucket.media.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# このディストリビューションの CloudFront からだけ読める
data "aws_iam_policy_document" "cloudfront_read" {
  for_each = {
    web   = aws_s3_bucket.web.arn
    media = aws_s3_bucket.media.arn
  }

  statement {
    actions   = ["s3:GetObject"]
    resources = ["${each.value}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket     = aws_s3_bucket.web.id
  policy     = data.aws_iam_policy_document.cloudfront_read["web"].json
  depends_on = [aws_s3_bucket_public_access_block.web]
}

resource "aws_s3_bucket_policy" "media" {
  bucket     = aws_s3_bucket.media.id
  policy     = data.aws_iam_policy_document.cloudfront_read["media"].json
  depends_on = [aws_s3_bucket_public_access_block.media]
}
