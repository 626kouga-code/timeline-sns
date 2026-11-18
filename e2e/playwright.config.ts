// E2E テストの設定（docs/08 の「E2E テスト」）。
// バックエンド（8080）とフロントの本番ビルド（5173）を Playwright が起動する。
// どちらかのポートが使用中なら起動に失敗する（開発用の DB につながったサーバーでテストしないため）。
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

const CI = !!process.env.CI
const LOAD = !!process.env.E2E_LOAD
const isWindows = process.platform === 'win32'
const dir = (path: string) => fileURLToPath(new URL(path, import.meta.url))

const browser = { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } }

// 2 段で実行する。
// 1. e2e: 全シナリオで動作を確かめる。失敗したテストはスクリーンショット・トレース・動画を残す
// 2. perf: 表示時間を計るシナリオ（@perf）だけを、録画なし・1 件ずつ流して 2 秒以内かを判定する。
//    動画・トレースの記録中は表示時間が 2〜3 倍になり、正しく計れないため分けている（support/perf.ts）
const e2eProject = {
  name: 'e2e',
  use: {
    ...browser,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
} as const
const perfProject = {
  name: 'perf',
  grep: /@perf/,
  workers: 1,
  // e2e と同時に動かすと負荷が混ざるので、e2e が終わってから流す（負荷中モードでは e2e は動かさない）
  dependencies: LOAD ? [] : ['e2e'],
  use: { ...browser, screenshot: 'only-on-failure', trace: 'off', video: 'off' },
} as const

export default defineConfig({
  testDir: './tests',
  workers: CI ? 1 : 2,
  retries: CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  globalSetup: './support/global-setup.ts',
  reporter: [['list'], ['html', { open: 'never' }], ['./support/perf-reporter.ts']],
  use: {
    baseURL: 'http://localhost:5173',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },
  // 負荷中モード（npm run test:load）では、k6 の負荷をかけながら perf だけを流す
  projects: LOAD ? [perfProject] : [e2eProject, perfProject],
  webServer: [
    {
      name: 'backend',
      // --no-daemon: テストが終わったときに、アプリごと確実に止まるようにする
      command: `${isWindows ? '.\\gradlew.bat' : './gradlew'} bootRun --no-daemon`,
      cwd: dir('../backend'),
      url: 'http://localhost:8080/actuator/health',
      env: {
        DB_URL: 'jdbc:postgresql://localhost:5433/timeline_e2e',
        STORAGE_BUCKET: 'timeline-e2e',
        LOGGING_LEVEL_COM_TIMELINESNS: 'INFO',
      },
      reuseExistingServer: false,
      timeout: 240_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      name: 'frontend',
      // 開発サーバーではなく本番ビルドを配る（表示時間を本番に近い条件で計るため）
      command: 'npm run build && npm run preview',
      cwd: dir('../frontend'),
      url: 'http://localhost:5173',
      reuseExistingServer: false,
      timeout: 180_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
})
