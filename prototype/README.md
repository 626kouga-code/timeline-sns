# UI プロトタイプ

要件定義書（[docs/](../docs/README.md)）の Must 機能の主要画面を、クリックして操作できる形で確認するためのモックです。

- **バックエンドなし**。データはメモリ上にだけあり、リロードすると初期状態に戻ります
- **使い捨て**。本実装は `frontend/`（Issue #3）で行い、ここのコードは流用しない前提です
- 技術構成は本実装に合わせています（React 19 / TypeScript / Vite 8 / Tailwind CSS 4 / React Router 8）

## 起動方法

```bash
cd prototype
npm install
npm run dev
```

http://localhost:5174 を開きます。本実装のフロントエンド（5173）と同時に起動できるよう 5174 に固定しています（`strictPort: true`。埋まっていても別ポートには移りません）。

## デモアカウント

パスワードは全員 `password123` です。ログイン画面のデモアカウント一覧、または画面右の「プロトタイプ操作」パネルから切り替えられます。

| ユーザー | 用途 |
| --- | --- |
| `taro@example.com` | 主に使う一般ユーザー。未読通知あり |
| `hanako` / `dev_ken` / `neko_suki` / `yuki_photo` | 一般ユーザー |
| `spam_bot99@example.com` | 通報されているユーザー |
| `admin@example.com` | 管理者（管理画面 `/admin/reports`） |

## 対象画面

| 画面 | パス |
| --- | --- |
| 全体タイムライン / ホームタイムライン | `/` `/home` |
| ログイン / 新規登録 | `/login` `/signup` |
| 投稿作成 | モーダル |
| 投稿詳細（コメント・返信） | `/posts/:id` |
| プロフィール / フォロー・フォロワー一覧 | `/users/:handle` `/users/:handle/following` `/users/:handle/followers` |
| ユーザー検索 | `/search?q=` |
| プロフィール編集 | `/settings/profile` |
| 通知 | `/notifications` |
| 管理画面 | `/admin/reports` |

メール確認・パスワード再設定・ユーザーID設定・アカウント設定は「対象外」の表示だけです。Google ログイン、メール送信、画像の Exif 除去・サムネイル生成もモックでは行いません。
