# 開発ルール

## Issue登録

- バグ報告・機能追加は、作業を始める前に必ずGitHub Issueを登録する。
- 小さなtypo修正やドキュメントの誤字など軽微な変更はIssue不要。
- Issueには最低限「背景・目的」「対応内容」を記載する。

## ブランチ命名規則

形式: `type/issue番号-短い説明`

- 例: `feature/12-add-login`, `fix/8-postgres-connection-timeout`
- `type`は以下から選ぶ:
  - `feature` — 新機能追加
  - `fix` — バグ修正
  - `refactor` — リファクタリング
  - `chore` — 依存更新・設定変更などその他
- 説明部分は英語の短いkebab-caseで、内容が分かるようにする。
- 対応するIssueがない場合はブランチを切らない（Issue登録を先に行う）。

## mainブランチ

- mainへの直接pushは禁止。必ずブランチを切ってPR経由でマージする。

## Pull Request

- PRの説明に `closes #issue番号` を記載し、マージ時にIssueが自動closeされるようにする（GitHubのキーワード連携機能）。
- PRタイトルはブランチと同様にtypeを先頭につける（例: `feat: ログイン機能を追加`）。

## 自動化設定（GitHubリポジトリ設定）

- 「マージ後にheadブランチを自動削除」を有効化済み（`gh api -X PATCH repos/<owner>/<repo> -f delete_branch_on_merge=true`）。
- PRで `closes #issue番号` と書けば、マージ時にIssueが自動でcloseされる。

## 開発サーバーのポート固定ルール

このプロジェクトのフロント/バックエンド開発サーバーは以下のポートに固定する。

- フロントエンド（Vite）: `5173`（[frontend/vite.config.ts](frontend/vite.config.ts) で `server.port: 5173` / `server.strictPort: true` を設定する。ポートが埋まっている場合、Viteは別ポートへ自動移動せず起動失敗する）
- バックエンド（Spring Boot）: `8080`（[backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) の `server.port: 8080` で固定する。同様に自動フォールバックしない）

Claude Codeがこれらのdevサーバーを起動・再起動する際は、必ず [.claude/skills/dev-server-ports/SKILL.md](.claude/skills/dev-server-ports/SKILL.md) のルールに従うこと（ポート競合時に別ポートへ逃がさない、既存プロセスの扱い方など）。
