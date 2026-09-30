# SNSアプリ（仮称）

X（旧Twitter）風のタイムライン型SNS。短文と画像を投稿し、フォロー・いいね・コメントで交流できるWebアプリです。React製フロントエンドとJava/Spring Bootバックエンドで構成し、データはPostgreSQL、投稿画像はAWS S3に保存します。個人で開発・運用し、誰でも登録できる公開型サービスとして複数のユーザーが使うことを想定しています。

> 現在は要件定義の段階です。実装はまだ始めていません。以下のセットアップ手順・ディレクトリ構成は、要件定義に基づく予定の内容です。

## 主な機能

MVP（最初の公開版）で実装する機能です。詳細と優先度は[機能要件](./docs/02_functional-requirements.md)を参照してください。

- アカウント：メール＋パスワードでの登録（メールアドレス確認つき）、Googleログイン、パスワードリセット、退会
- 投稿：テキスト（280文字まで）と画像（4枚まで）の投稿・削除、投稿URLのOGPプレビュー
- タイムライン：ホーム（自分とフォロー中のユーザー）と全体の2種類、無限スクロール
- リアクション：いいね、コメント、コメントへの返信（スレッド表示）
- ユーザー：プロフィールの表示・編集、フォロー／フォロワー
- 通知：いいね・コメント・返信・フォローを通知一覧と未読バッジで表示
- 安全対策：ブロック、通報、管理画面（投稿の削除・アカウントの凍結）
- 未ログインでも全体タイムライン・投稿詳細・プロフィールを閲覧できる

## 要件定義書

詳細な要件定義は以下に分割してあります（目次は[docs/README.md](./docs/README.md)）。

1. [概要・目的・想定利用者・リリース計画](./docs/01_overview.md)
2. [機能要件](./docs/02_functional-requirements.md)
3. [非機能要件](./docs/03_non-functional-requirements.md)
4. [画面一覧と画面遷移](./docs/04_screens.md)
5. [データモデル](./docs/05_data-model.md)
6. [API設計](./docs/06_api.md)
7. [技術スタックと開発環境](./docs/07_tech-stack.md)（バージョン一覧はこちら）
8. [テスト方針](./docs/08_testing.md)
9. [CI/CD](./docs/09_ci-cd.md)
10. [運用（監視・バックアップ）](./docs/10_operations.md)
11. [未決事項](./docs/11_open-issues.md)

## 技術スタック

詳細なバージョンは[技術スタックと開発環境](./docs/07_tech-stack.md)を参照してください。

| レイヤー | 主な技術 |
| --- | --- |
| フロントエンド | React 19 + TypeScript + Vite、Tailwind CSS、TanStack Query（サーバーデータの取得とキャッシュ）、React Router |
| バックエンド | Java 25 + Spring Boot 4.1（Gradle）、Spring Security（JWT認証・Googleログイン）、Spring Data JPA、Flyway |
| データベース | PostgreSQL 18 |
| 画像保存 | AWS S3（ローカル開発ではS3互換のMinIO） |
| メール送信 | AWS SES（ローカル開発ではMailpit） |
| 認証方式 | アクセストークン（JWT、15分）はフロントのメモリに保持し、リフレッシュトークン（14日）はHttpOnly Cookieで扱う |
| インフラ | AWS（構成は検討中。[未決事項](./docs/11_open-issues.md)を参照） |

## セットアップ・起動方法（予定）

### 前提

- Node.js 24（フロントエンド）
- Java 17以上（Gradleの実行用）。バックエンドのビルドに使うJava 25は、Gradleのtoolchain機能で自動ダウンロードされる。Gradleは同梱のGradle Wrapperを使う
- Docker / Docker Compose（PostgreSQL・MinIO・Mailpit用）

### 1. ローカル用サービスの起動

```bash
docker compose up -d
```

`docker-compose.yml`で次のコンテナを起動します。

| サービス | 用途 | ホスト側のポート |
| --- | --- | --- |
| PostgreSQL 18 | データベース（DB・ユーザー・パスワードはすべて `timeline`） | `5433` |
| Mailpit | 確認メール・パスワードリセットメールの受信確認（受信箱は http://localhost:8025） | `1025`（SMTP）、`8025`（Web UI） |

PostgreSQL は、他のプロジェクトでよく使われる `5432` と衝突しないよう `5433` にしています。投稿画像の保存先（S3互換ストレージ）は、MinIO のイメージが配布されなくなったため未導入です（選定は別Issueで行います）。

### 2. バックエンド起動

```bash
cd backend
./gradlew bootRun
```

`http://localhost:8080`でREST API（`/api/...`）が起動します。`bootRun`ではローカル用の設定（`application-local.yml`。JWTの署名鍵の開発用の値を含む）が有効になります。本番などそれ以外の環境では、環境変数`JWT_SECRET`に32バイト以上の署名鍵を設定しないと起動に失敗します。DBのテーブルは起動時にFlywayのマイグレーションで作成されます。ポートは固定のため、既に8080が使用中の場合は起動に失敗します（詳細は[.claude/skills/dev-server-ports/SKILL.md](.claude/skills/dev-server-ports/SKILL.md)）。

### 3. フロントエンド起動

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173`でアプリが起動します。`/api`宛てのリクエストはViteの開発サーバーがバックエンド（8080）へプロキシします。本番環境でもフロントとAPIを同じドメインで公開するため、開発時も同じ構成にしています。

### テスト

```bash
# バックエンド（JUnit 5 + Testcontainers。Dockerが起動している必要があります）
cd backend
./gradlew test

# フロントエンド（Vitest）
cd frontend
npm test
```

## ディレクトリ構成（予定）

```
.
├── frontend/              # フロントエンド（React + TypeScript + Vite）
│   └── src/
│       ├── pages/          # 画面（タイムライン・投稿詳細・プロフィール等）
│       ├── components/     # 投稿カード・コメントスレッド等のUIコンポーネント
│       ├── hooks/          # TanStack Queryによるデータ取得フック
│       └── api/            # バックエンドAPIクライアント
├── backend/               # バックエンド（Java / Spring Boot）
│   └── src/main/
│       ├── java/           # 認証・投稿・タイムライン・通知・管理などの機能ごとのパッケージ
│       └── resources/
│           └── db/migration/  # Flywayのマイグレーション
├── docs/                  # 要件定義書（11分割）
├── .claude/skills/        # Claude Code用のスキル（ポート固定ルール・品質チェック手順）
├── .github/workflows/     # CI/CD（GitHub Actions）
└── docker-compose.yml     # PostgreSQL・MinIO・Mailpit起動用（ローカル開発用）
```

## 開発の進め方

- テスト：PRごとにGitHub Actionsでバックエンド（JUnit 5 + Testcontainers）とフロントエンド（Vitest）のテストを実行し、失敗したらマージしない（[テスト方針](./docs/08_testing.md)）
- デプロイ：mainへのマージで本番へ自動デプロイする（[CI/CD](./docs/09_ci-cd.md)。具体的な手順はAWS構成の決定後に定義）
- 運用：ログと監視はCloudWatch、異常時はSNSからメールで通知する。DBは毎日バックアップする（[運用](./docs/10_operations.md)）
- 開発サーバーのポートは、フロント`5173`・バックエンド`8080`に固定する（[.claude/skills/dev-server-ports/SKILL.md](.claude/skills/dev-server-ports/SKILL.md)）
- コミット・PR作成前に品質チェックを行う（[.claude/skills/quality-check/SKILL.md](.claude/skills/quality-check/SKILL.md)）
