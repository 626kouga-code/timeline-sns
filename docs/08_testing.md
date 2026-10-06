# テスト方針

[目次に戻る](README.md)

| 対象 | ツール | 範囲 |
| --- | --- | --- |
| バックエンドの単体テスト | JUnit 5、Mockito | サービス層のロジック（権限確認、ブロック時の除外、返信の階層など） |
| バックエンドの結合テスト | JUnit 5、Testcontainers（PostgreSQL、S3Mock）、Spring Boot Test | API から DB までを通したテスト。認証・認可、主要な API |
| フロントエンド | Vitest、React Testing Library | コンポーネント、フォームの入力チェック、画面ごとの主な操作 |

- テストは PR ごとに CI で自動実行し、失敗したらマージしない
- 認証・権限・ブロックまわりは重点的にテストを書く
