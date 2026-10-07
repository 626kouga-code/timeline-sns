# 技術スタックと開発環境

[目次に戻る](README.md)

## 技術スタックとバージョン

2026-09-30 時点の最新安定版を採用する。

| 区分 | 技術 | バージョン | 補足 |
| --- | --- | --- | --- |
| バックエンド | Java | 25（LTS） | |
| | Spring Boot | 4.1.x | Spring Framework 7。サポートは 2027-07 まで |
| | Gradle | 9.x | Kotlin DSL |
| | 主なライブラリ | — | Spring Security（JWT。Google ログイン用の OAuth2 Client は Could）、Spring Data JPA、Flyway、Bean Validation、AWS SDK for Java v2（S3。SES はメール送信を作るときに使う） |
| データベース | PostgreSQL | 18.x | |
| フロントエンド | React | 19.x | |
| | TypeScript | 7.0.x | 導入時に ESLint などの対応状況を確認し、問題があれば 5.x/6.x 系に下げる |
| | Vite | 8.x | |
| | Tailwind CSS | 4.x | |
| | TanStack Query | 5.x | サーバーデータの取得とキャッシュ |
| | React Router | 8.x | |
| 開発ツール | Node.js | 24（LTS） | |
| | Docker / Docker Compose | 最新版 | |
| インフラ | AWS | — | 構成は未決（[未決事項](11_open-issues.md)） |
| 外部サービス | Google OAuth 2.0 | — | Google ログイン（Could。MVP では使わない） |

## 開発環境

モノレポ構成とする。

```
SNSアプリ/
├── frontend/            # React + Vite
├── backend/             # Spring Boot
├── docs/                # 要件定義書などの設計資料
├── docker-compose.yml   # ローカル用の PostgreSQL・S3Mock・Mailpit
└── .github/workflows/   # CI/CD
```

- ローカルでは Docker Compose で次のサービスを起動する
  - PostgreSQL
  - S3Mock（Adobe）：S3 互換のストレージ。画像を保存する。MinIO のイメージが配布されなくなったため、こちらを使う
  - Mailpit：送信メールを確認するためのテスト用メールサーバー
- 本番との違い（S3 / S3Mock、SES / Mailpit）は設定（環境変数）で切り替える。S3 は `app.storage.endpoint` を空にすると AWS の S3 につながる
