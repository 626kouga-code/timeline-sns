---
name: dev-server-ports
description: |
  このリポジトリのフロントエンド(Vite)・バックエンド(Spring Boot)開発サーバーを
  起動・再起動するときに必ず使う。npm run dev / vite / ./gradlew bootRun /
  gradlew.bat bootRun を実行する前、またはポート5173・8080が
  「already in use」「Port ... is already in use」「Web server failed to start.
  Port 8080 was already in use」等のエラーで競合したときに読むこと。
  「devサーバー起動して」「フロント/バックエンド立ち上げて」「動作確認して」
  「スクリーンショット撮って」のような、アプリを実際に動かす作業でも事前に参照する。
---

# 開発サーバーのポート固定ルール

このプロジェクトのフロント/バックエンド開発サーバーは、ポートを固定して運用する。
ポートが埋まっていても**別の空きポートに逃げて起動してはいけない**。

## 固定ポート

- フロントエンド(Vite): `5173`
  - [frontend/vite.config.ts](../../../frontend/vite.config.ts) で `server.port: 5173` /
    `server.strictPort: true` を設定する。ポートが埋まっている場合、Viteは
    別ポートへ自動移動せず起動失敗する(意図した挙動)。
- バックエンド(Spring Boot): `8080`
  - [backend/src/main/resources/application.yml](../../../backend/src/main/resources/application.yml)
    の `server.port: 8080` で固定する。同様に自動フォールバックしない。

> 設定ファイルを変更するときも、このポート固定（`strictPort: true` を含む）は外さないこと。

## なぜ固定するか

フロントの `frontend/vite.config.ts` は `/api` を `http://localhost:8080` へプロキシする。
どちらかのサーバーが別ポートで起動してしまうと、プロキシ先や `fetch('/api/...')`
の呼び出し先がずれて「動いているように見えて実は繋がっていない」状態になり、
気づきにくいバグの原因になる。さらに、認証のリフレッシュトークンを Cookie で扱うため、
フロントとAPIのオリジンがずれると Cookie が送られずログインが壊れる。
だからこそ、ポートがずれるくらいなら起動失敗する方が安全、という設計にしている。

## 起動前にやること

1. 対象ポートを使用しているプロセスを確認する。
   - Windows (このリポジトリの標準): `netstat -ano | grep ':<port>' | grep LISTENING`
   - PIDが分かったら `tasklist //PID <pid>` 等で何のプロセスか確認する。
2. **今回の作業セッションで自分が過去に起動した、古いdevサーバーだと判断できる場合のみ**、
   そのプロセスを停止してから既定ポートで起動し直す。
   - 停止: `taskkill //PID <pid> //F`
   - 起動: `cd frontend && npm run dev`(フロント) /
     `cd backend && ./gradlew bootRun`(バックエンド。PowerShellでは `.\gradlew.bat bootRun`)
   - バックエンドは PostgreSQL などに接続するため、先にリポジトリ直下で
     `docker compose up -d` が済んでいることを確認する。
3. **見覚えのないプロセス、または自分が起動したものか確信が持てないプロセス**が
   ポートを使用している場合は、勝手に終了させない。ユーザーに状況(PID・プロセス名・
   ポート)を報告し、停止してよいか確認する。
4. `--port` オプションや設定ファイルの一時変更で「空いている別のポートに逃がす」
   対応は行わない。うまくいかない場合は起動失敗のまま状況を報告する。

## 適用範囲

アプリの動作確認、UIのスクリーンショット取得、統合テストなど、Claude Codeが
自律的にdevサーバーを起動・再起動する場面すべてにこのルールを適用する。
