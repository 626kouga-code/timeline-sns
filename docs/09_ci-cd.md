# CI/CD

[目次に戻る](README.md)

GitHub Actions を使う。

| タイミング | 実行内容 |
| --- | --- |
| PR の作成・更新、main への push | CI（[ci.yml](../.github/workflows/ci.yml)）。バックエンド・フロントエンドの静的解析とテスト、E2E テスト |
| main へのマージ | 上記に加えて本番へデプロイする（フロントは S3＋CloudFront へ配置し、バックエンドはコンテナイメージを作って ECS Fargate へ反映する） |
| 手動（任意のタイミング） | パフォーマンステスト（[performance.yml](../.github/workflows/performance.yml)）、表示時間の判定・負荷中の計測を含む E2E（[e2e.yml](../.github/workflows/e2e.yml)）。[テスト方針](08_testing.md)を参照 |

- デプロイの流れ：フロントはビルドして S3 に置き、CloudFront の `/index.html` を無効化する。バックエンドは ARM のコンテナイメージを作って ECR に置き、ECS のサービスを更新する（[AWS 構成](12_aws-architecture.md#構築とデプロイ)。ワークフローは構築の Issue で作る）
- 本番は普段は止めておき、使うときに GitHub Actions の手動実行で起動・停止する（[起動と停止](12_aws-architecture.md#起動と停止)）
- AWS への認証には、GitHub Actions の OIDC 連携を使い、長期のアクセスキーは保存しない

## CI（PR ごと）

次の 5 つのジョブを並列に流す。静的解析とテストは別のジョブにしているので、どちらかが失敗しても、もう一方の結果は必ず出る。

| ジョブ | 内容 | コマンド |
| --- | --- | --- |
| `backend-lint` | バックエンドの静的解析（Checkstyle・SpotBugs） | `./gradlew checkstyleMain checkstyleTest spotbugsMain spotbugsTest` |
| `backend-test` | バックエンドの単体・結合テスト（JUnit・Mockito・Testcontainers） | `./gradlew test` |
| `frontend-lint` | フロントエンドの静的解析（ESLint・型チェック） | `npm run lint`、`npm run typecheck` |
| `frontend-test` | フロントエンドの単体テスト（Vitest）と本番ビルド | `npm test`、`npm run build` |
| `e2e` | Playwright の E2E テスト（全シナリオ）と、E2E のコードの型チェック | `npm run typecheck`、`npm run test:e2e` |

- **5 つすべてが成功しないと、PR は main にマージできない**（main のブランチ保護で必須のチェックにしている。管理者にも適用される）
- 同じ PR に続けて push すると、古い実行は取り消される
- E2E は表示時間の判定（perf プロジェクト）を流さない。ランナーの性能は実行ごとに揺れるので、性能はパフォーマンステストと同じく手動で確かめる
- CI の E2E は 1 並列で流し、失敗したテストは 1 回だけやり直す。やり直しで通ったテストは HTML レポートに「flaky」と出る
- ローカルで同じことを確かめる手順は、quality-check スキル（`.claude/skills/quality-check/SKILL.md`）にある。CI で落ちる前にローカルで流す

### 失敗したとき

1. PR の「Checks」タブ（または PR 下部のチェック一覧の「Details」）で、失敗したジョブとステップのログを見る
2. ジョブごとに、詳しいレポートを artifact に残す（実行結果のページの下部からダウンロードする。14 日保存）
   - `backend-lint-reports`: Checkstyle・SpotBugs の HTML レポート（失敗したときだけ）
   - `backend-test-reports`: JUnit のテストレポート（失敗したときだけ）
   - `e2e-report`: Playwright の HTML レポートと、失敗したテストのスクリーンショット・トレース・動画（毎回）。展開して `npx playwright show-report <展開したフォルダ>/e2e/playwright-report` で開く
3. 直したら同じブランチに push する。CI は自動でやり直される

ジョブ名はブランチ保護の必須のチェックの名前になっている。ジョブ名を変える・増やすときは、ブランチ保護の設定（Settings → Branches、または `gh api repos/<owner>/<repo>/branches/main/protection`）も合わせて直す。

## Dependabot

[dependabot.yml](../.github/dependabot.yml) で、使っている依存に更新が出たら PR を作る。

| 対象 | 場所 |
| --- | --- |
| npm | `frontend/`・`e2e/`・`perf/` |
| Gradle | `backend/` |
| GitHub Actions | `.github/workflows/` |
| Docker Compose のイメージ | `docker-compose.yml` |

- 毎週月曜 09:00（日本時間）に確認する。minor・patch の更新は対象ごとに 1 つの PR にまとめ、メジャー更新は個別の PR にする
- PR のタイトルは `chore(deps): ...` になる。Issue の登録とブランチ命名のルール（CLAUDE.md）の例外とする
- CI が通れば、そのままマージしてよい。メジャー更新は変更内容（リリースノート）を確かめてからマージする
- 次は理由があって固定しているので、更新の PR を作らない
  - `typescript` の 6.1 以上: typescript-eslint が 6.1 未満にしか対応していないため
- `e2e/` の `@playwright/test` と `playwright-core` は同じ版でないと型が合わないので、必ず同じ PR で上げる
- 脆弱性が見つかった依存は、週 1 回の確認を待たずにアラートと修正の PR が来る（Dependabot alerts・security updates を有効にしている）
