# テスト方針

[目次に戻る](README.md)

| 対象 | ツール | 範囲 |
| --- | --- | --- |
| バックエンドの単体テスト | JUnit 5、Mockito | サービス層のロジック（権限確認、ブロック時の除外、返信の階層など） |
| バックエンドのデータアクセス層のテスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、`@DataAccessTest` | SQL を書いたクラス（`*Query`）・JPA のリポジトリ・画像の保存先。本物の DB・S3 互換ストレージに書き込み、読み戻して確かめる（並び順・除外条件・ページングの境界・制約）。各テストはロールバックするので、データは残らない |
| バックエンドの結合テスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、Spring Boot Test | API から DB までを通したテスト。認証・認可、主要な API |
| フロントエンド | Vitest、React Testing Library | API モジュール（送るリクエストの形と、応答・エラーの受け取り）、UI コンポーネント 1 つずつ、フォームの入力チェック、画面ごとの主な操作 |
| パフォーマンス | k6 | タイムラインの応答時間（[非機能要件](03_non-functional-requirements.md)の「性能」）。PR ごとには動かさず、任意のタイミングで手動実行する（[パフォーマンステスト](#パフォーマンステスト)） |

- テストは PR ごとに CI で自動実行し、失敗したらマージしない（パフォーマンステストは除く）
- 認証・権限・ブロックまわりは重点的にテストを書く

## テストの書き方

テストは対象と同じパッケージ（フロントは同じフォルダ）に置き、`*Test.java`・`*.test.ts(x)` と名付ける。

### バックエンド

| 層 | 使うもの | 例 |
| --- | --- | --- |
| SQL・リポジトリ・画像の保存先 | `@DataAccessTest`（本物の PostgreSQL・S3Mock。テストごとにロールバック）、`Fixtures`（`new Fixtures(jdbc)`）でデータを入れる | `UserQueryTest`、`ObjectStorageTest` |
| サービスのロジック | Mockito で依存をモックにし、`Clock.fixed` で時刻を固定する。どの条件で書き込み・通知するかを確かめる | `CommentServiceTest`、`AuthServiceTest` |
| 画像処理・ユーティリティ | Spring を起動しない純粋な単体テスト。画像は `TestImages` で作る | `ImageProcessorTest`、`ApiExceptionHandlerTest` |
| API（結合） | `@IntegrationTest`（MockMvc + Testcontainers） | `PostApiTest` |

### フロントエンド（`src/test/utils.tsx`）

| 層 | 使うもの |
| --- | --- |
| API モジュール | `mockApi`（パスごとの fetch のモック）、`request`（n 回目のリクエストのメソッド・ヘッダー・本文） |
| UI コンポーネント | `renderWithProviders`（Query・トースト・ログイン状態・ルーターで包む。遷移先は `getByTestId('location')`）、`deferred`（応答を待たせて送信中の表示を確かめる） |
| 画面 | `renderApp`（アプリ全体を指定のパスで描画する） |

新しいテストが本当に不具合を見つけられるかは、実装を一時的に壊して（条件を外す・判定を反転する）落ちることを確かめてから戻す。

## パフォーマンステスト

[非機能要件](03_non-functional-requirements.md)の「タイムラインの初回表示は 2 秒以内（100 ユーザー同時接続時）」を k6 で確かめる。性能に効きそうな変更（タイムラインの SQL・インデックス・設定など）の前後や、リリース前などに手動で実行する。対象はローカルと GitHub Actions のランナー上の環境だけで、AWS の環境には負荷をかけない。

### シナリオ（`perf/timeline.ts`）

シナリオはフロントエンドに合わせて TypeScript で書く。k6 は `.ts` をそのまま実行するのでビルドは要らない。`perf/package.json` の依存（`@types/k6`・`typescript`）は型チェック専用で、`perf/` で `npm install` → `npm run typecheck` で確かめる（CI でも k6 の前に実行する）。

1. `setup()` で API を呼んでテストデータを作る。ユーザー登録、本文だけの投稿、フォローの順。ユーザー名には実行ごとに別の接頭辞（`pf<5 桁>_<連番>`）を付けるので、前回のデータとぶつからない
2. 各 VU（仮想ユーザー）が次の操作を繰り返す。間に 1〜2 秒の待ちを入れる
   1. ホームタイムラインの 1 ページ目
   2. 2 ページ目（`nextCursor` があるとき）
   3. 新着件数
   4. 全体タイムライン（ゲスト）

| `SCENARIO` | VU | 時間 | データ（ユーザー / 1 人の投稿 / 1 人のフォロー） | 用途 |
| --- | --- | --- | --- | --- |
| `smoke`（既定） | 5 | 30 秒 | 10 / 5 / 3 | スクリプトと環境が動くかの確認 |
| `load` | 0→100（1 分）→ 100 で 3 分 → 0 | 約 4.5 分 | 100 / 20 / 20 | 要件（100 同時接続）の確認 |

データの量は `-e USERS=` `-e POSTS_PER_USER=` `-e FOLLOWS=` で変えられる。アクセストークンの有効期限が 15 分なので、`setup()` と本体を合わせて 15 分以内に収める。

合否は次の閾値で決まる。1 つでも外れると k6 は終了コード 99 で終わる（CI のジョブも失敗になる）。

| 指標 | 閾値 |
| --- | --- |
| ホームタイムライン・全体タイムラインの応答時間（`http_req_duration{name:home_timeline}` など） | p95 が 2 秒未満 |
| 失敗したリクエストの割合（`http_req_failed`、`setup()` の分も含む） | 1% 未満 |
| `check` の成功率 | 99% より大きい |

### ローカルで実行する

k6 は docker compose の `k6` サービス（profile `perf`）で動かすので、インストールは要らない。開発用の DB を汚さないように、別のデータベース `timeline_perf` を使う。

```sh
# 1. DB・S3Mock を起動し、パフォーマンステスト用のデータベースを作る（作るのは初回だけ）
docker compose up -d
docker compose exec postgres createdb -U timeline timeline_perf

# 2. バックエンドを timeline_perf につないで起動する（スキーマは Flyway が作る）。
#    DEBUG ログは計測を歪めるので INFO にする。8080 の扱いは .claude/skills/dev-server-ports に従う
cd backend
DB_URL=jdbc:postgresql://localhost:5433/timeline_perf LOGGING_LEVEL_COM_TIMELINESNS=INFO ./gradlew bootRun
# PowerShell の場合:
#   $env:DB_URL="jdbc:postgresql://localhost:5433/timeline_perf"; $env:LOGGING_LEVEL_COM_TIMELINESNS="INFO"; .\gradlew.bat bootRun

# 3. 別のターミナルで、リポジトリ直下から実行する
docker compose run --rm k6 run /perf/timeline.ts                   # smoke
docker compose run --rm k6 run -e SCENARIO=load /perf/timeline.ts  # load

# 4. 片付け（テストデータは実行ごとに溜まる。バックエンドを止めてから消す）
docker compose exec postgres dropdb -U timeline timeline_perf
```

- Git Bash では `/perf/...` が Windows のパスに書き換えられて失敗する。先頭に `MSYS_NO_PATHCONV=1` を付けるか、PowerShell で実行する
- k6 をインストール済みなら、リポジトリ直下で `k6 run perf/timeline.ts` でもよい（対象は `-e BASE_URL=` で変えられる）。HTML レポートも出すには、環境変数 `K6_WEB_DASHBOARD=true` と `K6_WEB_DASHBOARD_EXPORT=perf/results/report.html` を付ける

### GitHub Actions で実行する

ワークフロー [performance.yml](../.github/workflows/performance.yml) は手動実行（`workflow_dispatch`）だけで動く。Actions タブの「performance」→「Run workflow」でシナリオを選ぶか、次のコマンドで実行する。

```sh
gh workflow run performance.yml -f scenario=load
```

ランナー上で PostgreSQL・S3Mock を docker compose で起動し、バックエンドを本番と同じ設定（プロファイルなし）で起動してから k6 を流す。HTML レポート（`report-<シナリオ>.html`）・結果の JSON・バックエンドのログは artifact（`performance-<シナリオ>-<実行番号>`、14 日保存）に残る。GitHub のランナーは性能が実行ごとに揺れるので、数値は傾向を見る参考値として扱う。

### 結果の見方

- 標準出力の要約で、`{ name:home_timeline }` の `p(95)` と、`http_req_failed`・`checks` に ✓ が付いているかを見る
- 時間ごとの推移は HTML レポートで見る。ローカルは `perf/results/report.html`（実行のたびに上書き）をブラウザで開く。応答時間・リクエスト数・VU 数の推移のグラフと、エンドポイントごとの集計がある
- 詳しい値は `perf/results/summary-<シナリオ>.json`（git の管理外）に残る
- 遅いリクエストを調べるときは、バックエンドのアクセスログ（`RequestLoggingFilter` が出す `durationMs`）を見る

参考までに、2026-11-18 に開発用 PC で `load` を実行したときのホームタイムラインの p95 は約 100ms、失敗は 0% だった。
