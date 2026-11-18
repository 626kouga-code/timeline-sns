---
name: quality-check
description: |
  このリポジトリでコードをコミット・PR作成する前に必ず使う。フロントエンド(ESLint・型チェック・Vitest)・
  バックエンド(Gradle check: Checkstyle/SpotBugs/JUnit)の品質チェックを
  一通り実行する手順をまとめたもの。「品質チェックして」「PRを作る前に確認して」
  「コミットする前にチェックして」と言われたときに参照する。
---

# 品質チェック手順

コミット・PR作成前に、変更のあった領域ごとに以下を実行する。

> CI（GitHub Actions、#5 で作成予定）でも同じチェックを実行する。

## フロントエンド（`frontend/` 配下を変更した場合）

```bash
cd frontend
npm run lint        # ESLint（flat config: frontend/eslint.config.js）
npm run typecheck   # tsc --noEmit による型チェック
npm test            # Vitest + React Testing Library
```

エラーが出たら修正してから再実行する。

## バックエンド（`backend/` 配下を変更した場合）

```bash
cd backend
./gradlew check     # PowerShellでは .\gradlew.bat check
```

`check` タスクで次がまとめて走る。

- Checkstyle：コーディング規約のチェック（設定は `backend/config/checkstyle/checkstyle.xml`）
- SpotBugs：バグになりやすいコードの検出（除外設定は `backend/config/spotbugs/exclude.xml`）
- テスト：JUnit 5 + Testcontainers。PostgreSQL・S3Mock をコンテナで起動するため、**Docker が起動している必要がある**

違反やテスト失敗があるとビルド自体が失敗するように設定する（`ignoreFailures = false`）。

## パフォーマンステスト（`perf/` 配下を変更した場合）

```bash
cd perf
npm install         # 初回のみ（@types/k6・typescript。型チェック専用）
npm run typecheck   # tsc --noEmit による型チェック
```

シナリオの中身を変えたときは、docs/08 の手順で smoke も実行して動くことを確かめる。

## E2E テスト（`e2e/` 配下を変更した場合）

```bash
cd e2e
npm install                          # 初回のみ
npx playwright install chromium      # 初回のみ（ブラウザのダウンロード）
npm run typecheck                    # tsc --noEmit による型チェック
```

シナリオを変えたときは、8080・5173 を空けてから `npm test` を実行して通ることを確かめる（docs/08）。
`frontend/` の画面の文言・構造を変えたときも、E2E が壊れていないか `npm test` で確かめる。

## Terraform

AWS 構成は未定（[docs/11_open-issues.md](../../../docs/11_open-issues.md)）。Terraform を導入したら、
`terraform fmt -check -diff` と `terraform validate` の手順をここに追加する。

## 適用範囲

PRを作る前、および「品質チェックして」と依頼されたときは、変更されたディレクトリに対応するチェックをすべて実行する。複数領域にまたがる変更(例: フロント+バックエンド)では、該当するチェックをすべて行う。
