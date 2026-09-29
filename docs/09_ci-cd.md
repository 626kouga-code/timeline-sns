# CI/CD

[目次に戻る](README.md)

GitHub Actions を使う。

| タイミング | 実行内容 |
| --- | --- |
| PR の作成・更新 | バックエンド：ビルド、テスト（Testcontainers を含む）、静的解析。フロントエンド：型チェック、ESLint、Vitest、ビルド |
| main へのマージ | 上記に加えて本番へデプロイする（フロントは S3＋CloudFront へ配置し、バックエンドはコンテナイメージを作ってサーバーへ反映する） |

- デプロイの具体的な手順は AWS 構成が決まってから定義する
- AWS への認証には、GitHub Actions の OIDC 連携を使い、長期のアクセスキーは保存しない
