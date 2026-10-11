# テスト方針

[目次に戻る](README.md)

| 対象 | ツール | 範囲 |
| --- | --- | --- |
| バックエンドの単体テスト | JUnit 5、Mockito | サービス層のロジック（権限確認、ブロック時の除外、返信の階層など） |
| バックエンドのデータアクセス層のテスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、`@DataAccessTest` | SQL を書いたクラス（`*Query`）・JPA のリポジトリ・画像の保存先。本物の DB・S3 互換ストレージに書き込み、読み戻して確かめる（並び順・除外条件・ページングの境界・制約）。各テストはロールバックするので、データは残らない |
| バックエンドの結合テスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、Spring Boot Test | API から DB までを通したテスト。認証・認可、主要な API |
| フロントエンド | Vitest、React Testing Library | API モジュール（送るリクエストの形と、応答・エラーの受け取り）、UI コンポーネント 1 つずつ、フォームの入力チェック、画面ごとの主な操作 |

- テストは PR ごとに CI で自動実行し、失敗したらマージしない
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
