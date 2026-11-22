# テスト方針

[目次に戻る](README.md)

| 対象 | ツール | 範囲 |
| --- | --- | --- |
| バックエンドの単体テスト | JUnit 5、Mockito | サービス層のロジック（権限確認、ブロック時の除外、返信の階層など） |
| バックエンドのデータアクセス層のテスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、`@DataAccessTest` | SQL を書いたクラス（`*Query`）・JPA のリポジトリ・画像の保存先。本物の DB・S3 互換ストレージに書き込み、読み戻して確かめる（並び順・除外条件・ページングの境界・制約）。各テストはロールバックするので、データは残らない |
| バックエンドの結合テスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、Spring Boot Test | API から DB までを通したテスト。認証・認可、主要な API |
| フロントエンド | Vitest、React Testing Library | API モジュール（送るリクエストの形と、応答・エラーの受け取り）、UI コンポーネント 1 つずつ、フォームの入力チェック、画面ごとの主な操作 |
| パフォーマンス | k6 | タイムラインの応答時間（[非機能要件](03_non-functional-requirements.md)の「性能」）。PR ごとには動かさず、任意のタイミングで手動実行する（[パフォーマンステスト](#パフォーマンステスト)） |
| E2E | Playwright（Chromium）、@axe-core/playwright | 本物のブラウザで画面を操作し、API・DB まで通すシナリオテスト。表示時間（ブラウザ性能）とアクセシビリティも確かめる。シナリオは PR ごとに CI で実行し、表示時間の判定は任意のタイミングで手動実行する（[E2E テスト](#e2e-テスト)） |

- テストと静的解析は PR ごとに CI（[CI/CD](09_ci-cd.md)）で自動実行し、失敗したらマージしない。パフォーマンステスト（k6）と E2E の表示時間の判定は除く（手動で実行する）
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

フロントエンドのテストで、非同期に出る要素を待つときは `await screen.findBy...(...)` だけを書き、`expect(await screen.findBy...(...)).toBeInTheDocument()` とはしない。`findBy` は見つからなければそれ自体が失敗する。見つけた要素を後から確かめ直すと、その間に再描画が挟まったとき（ログイン直後など）に要素が画面から外れて、CI の遅いマシンでだけ失敗する（#89）。要素の中身を確かめるときは、もう一度 `getBy...` で取り直す。

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

## E2E テスト

本物のブラウザ（Chromium、1280×800）で画面を操作し、バックエンド・DB・画像の保存先まで通して確かめる。シナリオの中で表示時間（ブラウザ性能）も計る。画面や API に大きな変更を入れた後や、リリース前などに手動で実行する。テストは `e2e/` にあり、TypeScript で書く（`frontend/` とは別のパッケージ）。

### シナリオ（`e2e/tests/`）

[機能要件](02_functional-requirements.md)の Must のうち、実装済みのものを一通り通す。

| ファイル | 内容 |
| --- | --- |
| `auth.spec.ts` | F-01 画面からの登録、F-03 ログアウト・ログイン・パスワード違い、ログインが必要な画面からログイン後に戻る |
| `post.spec.ts` | F-10 投稿、F-11 画像付き投稿（画像が実際に読み込めるか）、F-13 詳細、F-12 削除 |
| `timeline.spec.ts` | F-20 ホーム（フォロー中と自分だけ・新しい順）、F-21 ゲストの全体タイムライン、F-22 新着のお知らせ（30 秒の確認は時計を早送りする） |
| `reaction.spec.ts` | F-30 いいね・取り消し、F-31 コメント、F-32 返信、F-33 コメント削除 |
| `user.spec.ts` | F-40 プロフィール、F-42 フォロー・解除、F-43 フォロー・フォロワー一覧、F-41 表示名・自己紹介・アイコン画像の変更、F-44 検索 |
| `notification.spec.ts` | F-51 未読の件数、F-50 通知一覧と既読 |
| `a11y.spec.ts` | 主な 10 画面のアクセシビリティ検査（下記） |

- 前提のデータ（他のユーザーの投稿・フォローなど）は API で作る（`support/api.ts`）。テストごとに新しいユーザーを作るので、テスト同士が影響しない
- ログイン状態はテストごとに API でログインして作る。ログイン状態の保存ファイル（storageState）を使い回すと、リフレッシュトークンの再利用とみなされて全セッションが失効するため使わない
- 全体タイムラインは並列で動く他のテストの投稿で埋まるので、特定の投稿を探すときはプロフィールや詳細画面を使う
- いいね・フォローのボタンは通信の完了を待たずに切り替わる（楽観的更新）。操作の直後に画面を移るときは、応答を待ってから移る（`page.waitForResponse`）

### 実行の流れ

`npm test` は次の順で動く。

1. `scripts/reset-db.mjs`: PostgreSQL・S3Mock を起動し、E2E 専用のデータベース `timeline_e2e` を作り直す。画像は専用バケット `timeline-e2e` に入る。開発用のデータは使わず、片付けも要らない
2. Playwright がバックエンド（`bootRun`、8080）とフロントの本番ビルド（`vite build` → `vite preview`、5173）を起動する
   - どちらかのポートが使用中なら、別のポートに逃げずに失敗する。開発中のサーバーは止めてから実行する（開発用 DB につながったサーバーでテストしないため）
   - 開発サーバー（`npm run dev`）ではなく本番ビルドを使うのは、表示時間を本番に近い条件で計るため
3. 起動したばかりのバックエンドは JIT（実行時の最適化）が効いておらず、最初の数回の応答が数倍遅い。本番は起動しっぱなしなので、主な API を 30 回ずつ呼んで温めてからテストする（`support/warmup.ts`）
4. **e2e**: 全シナリオを流す（ローカルは 2 並列）
5. **perf**: e2e がすべて通ったら、表示時間を計るシナリオ（`@perf`）を録画なし・1 件ずつ流して判定する。動画・トレースの記録中は表示時間が 2〜3 倍になり、正しく計れないため分けている
6. 終わると、Playwright が起動したサーバーを止める

### ローカルで実行する

```sh
cd e2e
npm install                       # 初回のみ
npx playwright install chromium   # 初回のみ（ブラウザのダウンロード）

npm test                          # 全シナリオ + 表示時間の判定（約 3 分）
npm run test:load                 # k6 の 100 VU を流しながら表示時間だけを計る（約 9 分）
npm run report                    # HTML レポートを開く

# 一部だけ流す例
npx playwright test tests/post.spec.ts --project e2e
npx playwright test --project perf --no-deps   # 表示時間の判定だけ
```

### ブラウザ性能

要件（[非機能要件](03_non-functional-requirements.md)）の「タイムラインの初回表示は 2 秒以内（100 ユーザー同時接続時）」を、画面の表示まで含めて確かめる。

| 計測 | 内容 |
| --- | --- |
| `home_timeline` | ホームを開いてから（ログイン状態の復元を含め）投稿が表示されるまで。要件の対象 |
| `global_timeline_guest` | ゲストが全体タイムラインを開いてから表示されるまで |
| `post_detail` | タイムラインの投稿を押してから詳細が表示されるまで |
| `profile` | プロフィールを開いてから投稿が表示されるまで |
| `post_to_timeline` | 投稿ボタンを押してから自分の投稿がタイムラインに出るまで |

- どれも 2 秒未満で合格（`PERF_BUDGET_MS` で変えられる）。ページの読み込みを伴う計測では FCP（最初の描画）・LCP（主な内容の描画）も記録する
- 結果は実行の最後に表で出し、`e2e/perf-results/browser-perf.json`（負荷中は `browser-perf-load.json`）に残す。HTML レポートの各テストにも添付される
- `npm run test:load` は、k6 の load シナリオ（[パフォーマンステスト](#パフォーマンステスト)）を docker compose で裏で動かし、100 VU に達してから perf のシナリオを流す。終わったら k6 を止める（k6 のログは `perf-results/k6-load.log`）
- 参考: 2026-11-18 に開発用 PC で計測したとき、ホームタイムラインの表示は負荷なしで約 0.7〜0.9 秒、100 VU の負荷中で約 1.1 秒だった

### エラーの自動検出

すべてのテストで、ブラウザのエラーを自動で見張る（`support/fixtures.ts` の `errorGuard`）。次のどれかが 1 件でもあれば、画面の確認が通っていてもテストは失敗する。内容はレポートの添付「ブラウザのエラー」で見られる。

- 捕まえられなかった JavaScript の例外
- `console.error`
- API の 5xx

4xx の応答でブラウザが出す「Failed to load resource」は、未ログイン時の refresh 401 などの正常な動きなので除外する。意図してエラーを起こすテストでは、`test.use({ allowErrors: [/パターン/] })` で許可し、理由をコメントで書く。

### アクセシビリティ

`a11y.spec.ts` で、データを入れた状態の主な画面（ログイン・登録・全体タイムライン・検索結果・ホーム・投稿モーダル・投稿詳細・プロフィール・プロフィール編集・通知一覧）を axe-core で検査する。基準は WCAG 2.1 の A・AA。違反があれば、レポートに一覧（ルール・重要度・該当要素）が添付される。

- 除外しているルールはない。違反が 1 つでもあれば失敗する（既存の違反は #73 で直した）
- 直すときの注意
  - 文字の色は、白い背景で `slate-500`・`sky-700` より薄くしない。白い文字を載せるボタンの背景は `sky-700` 以上にする
  - 見えている文字を読み上げ名の中にそのまま含める（音声入力で押せるように）。`aria-label` で別の文言に上書きせず、足りない説明は `sr-only` の文字で後ろに足す（例：いいねボタンは「3 件のいいね」、通知のナビは「通知 3 件の未読」、フォローボタンは「フォロー @hanako」）。axe は括弧の中を無視して比べるので、「いいね（3 件）」のような名前は見えている「3」を含まないと判定される
  - アイコン画像だけのリンクは、隣に同じ行き先の名前のリンクがあれば `aria-hidden` と `tabIndex={-1}` で読み上げと Tab から外し、なければ `aria-label` で名前を付ける
- それ以外のルールの違反は、新しく増えたものとしてすべて失敗になる

### 失敗したとき

失敗したテストだけ、スクリーンショット・トレース・動画を残す（e2e プロジェクト。perf はスクリーンショットだけ）。

- `npm run report` で HTML レポートを開き、失敗したテストを選ぶと、エラー・スクリーンショット・動画・添付が見られる
- トレースは、レポートから開くか `npx playwright show-trace test-results/<テスト名>/trace.zip` で開く。操作の 1 手ごとの画面・DOM・API の通信・コンソールをコマ送りで見られる

### GitHub Actions で実行する

ワークフロー [e2e.yml](../.github/workflows/e2e.yml) は手動実行（`workflow_dispatch`）だけで動く。Actions タブの「e2e」→「Run workflow」で `mode` を選ぶか、次のコマンドで実行する。

```sh
gh workflow run e2e.yml -f mode=normal   # 全シナリオ + 表示時間の判定
gh workflow run e2e.yml -f mode=load     # k6 の 100 VU を流しながら表示時間だけ
```

- CI では 1 並列で流し、失敗したテストは 1 回だけやり直す
- HTML レポート・失敗したテストの記録・計測結果は artifact（`e2e-<mode>-<実行番号>`、14 日保存）に残る。ダウンロードして `npx playwright show-report <展開したフォルダ>/e2e/playwright-report` で開く
- 失敗すると、実行した人に GitHub から通知が届く
- GitHub のランナーは性能が実行ごとに揺れるので、表示時間は傾向を見る参考値として扱う
