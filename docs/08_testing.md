# テスト方針

[目次に戻る](README.md)

| 対象 | ツール | 範囲 |
| --- | --- | --- |
| バックエンドの単体テスト | JUnit 5、Mockito | サービス層のロジック（権限確認、ブロック時の除外、返信の階層など） |
| バックエンドのデータアクセス層のテスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、`@DataAccessTest` | SQL を書いたクラス（`*Query`）・JPA のリポジトリ・画像の保存先。本物の DB・S3 互換ストレージに書き込み、読み戻して確かめる（並び順・除外条件・ページングの境界・制約）。各テストはロールバックするので、データは残らない |
| バックエンドの結合テスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、Spring Boot Test | API から DB までを通したテスト。認証・認可、主要な API |
| フロントエンド | Vitest、React Testing Library | コンポーネント、フォームの入力チェック、画面ごとの主な操作 |

- テストは PR ごとに CI で自動実行し、失敗したらマージしない
- 認証・権限・ブロックまわりは重点的にテストを書く
