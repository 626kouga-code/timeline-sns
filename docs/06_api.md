# API 設計

[目次に戻る](README.md)

REST（JSON）で提供する。一覧は `?cursor=<最後のid>&limit=20` のカーソル方式でページングする。

| 分類 | メソッドとパス | 概要 |
| --- | --- | --- |
| 認証 | `POST /api/auth/signup` | 新規登録 |
| | `POST /api/auth/verify-email` | メール確認 |
| | `POST /api/auth/login` | ログイン（アクセストークンを返し、リフレッシュトークンを Cookie に設定する） |
| | `POST /api/auth/refresh` | トークンの再発行 |
| | `POST /api/auth/logout` | ログアウト |
| | `POST /api/auth/password-reset/request` `POST /api/auth/password-reset/confirm` | パスワード再設定 |
| | `GET /oauth2/authorization/google` | Google ログイン開始 |
| タイムライン | `GET /api/timeline/home` `GET /api/timeline/global` | ホーム／全体タイムライン |
| | `GET /api/timeline/home/new-count?since=<id>` `GET /api/timeline/global/new-count?since=<id>` | 新着件数（`since` より新しい投稿の数。新着表示に使う） |
| 投稿 | `POST /api/posts`（multipart） | 投稿作成（画像つき） |
| | `GET /api/posts/{id}` `DELETE /api/posts/{id}` | 取得・削除 |
| | `POST /api/posts/{id}/likes` `DELETE /api/posts/{id}/likes` | いいね・取り消し（冪等。応答は `{ liked, likeCount }`） |
| コメント | `GET /api/posts/{id}/comments` `POST /api/posts/{id}/comments` | 一覧（全件を古い順の平らな配列で返す。スレッドはフロントで組み立てる）・作成（`parentId` を指定すると返信） |
| | `DELETE /api/comments/{id}` | 削除（本人のみ。返信が付いていれば論理削除、なければ物理削除） |
| ユーザー | `GET /api/search/users?q=<キーワード>` | ユーザー検索（ユーザーID・表示名の部分一致） |
| | `GET /api/users/{handle}` `GET /api/users/{handle}/posts` | プロフィール（フォロー数・フォロワー数と、閲覧者との関係 `following` `followedBy` `blocking` `blockedBy` を含む）・投稿一覧（ブロック関係にあれば空）。`{handle}` は大文字小文字を区別しない。凍結ユーザーは 404 |
| | `GET /api/users/{handle}/following` `GET /api/users/{handle}/followers` | フォロー・フォロワー一覧（フォローした日時の新しい順。凍結ユーザーと、閲覧者とブロック関係にあるユーザーは除く） |
| | `PUT /api/users/{handle}/follow` `DELETE /api/users/{handle}/follow` | フォロー・解除（冪等。応答は `{ following, followerCount }`。自分自身は 400、ブロック関係にある相手は 403） |
| | `PUT /api/users/{handle}/block` `DELETE /api/users/{handle}/block` | ブロック・解除 |
| | `GET /api/me` `PATCH /api/me` `DELETE /api/me` | 自分の情報取得・更新（`{ displayName, bio }` を両方送る）・退会 |
| | `PUT /api/me/avatar`（multipart の `file`） `DELETE /api/me/avatar` | アイコン画像の変更・削除。画面で正方形に切り抜いて送る。サーバーで中身から形式を判定し（JPEG / PNG / WebP / GIF、5MB まで）、Exif を除いて 400×400 に作り直す。応答は更新後の自分の情報 |
| 画像 | `GET /api/media/{key}` | 保存した画像の配信（ゲスト可。キーは保存のたびに変わるので長期キャッシュ）。各応答の `avatarUrl` はこの URL（本番で CloudFront から配信するときは設定で切り替える） |
| 通知 | `GET /api/notifications` `GET /api/notifications/unread-count` `POST /api/notifications/read` | 一覧・未読数・既読化 |
| 通報 | `POST /api/reports` | 通報 |
| 管理 | `GET /api/admin/reports` `PATCH /api/admin/reports/{id}` | 通報一覧・対応状況の更新 |
| | `DELETE /api/admin/posts/{id}` `DELETE /api/admin/comments/{id}` | 管理者による削除 |
| | `PUT /api/admin/users/{id}/suspension` `DELETE /api/admin/users/{id}/suspension` | 凍結・解除 |
