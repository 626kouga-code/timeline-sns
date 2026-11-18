// webServer（バックエンド・フロント）が起動した後、テストの前に 1 回だけ呼ばれる。返す関数はテストが全部終わった後に呼ばれる。
// - 起動したばかりのバックエンドを温める（support/warmup.ts）
// - 負荷中モード（E2E_LOAD=1、`npm run test:load`）のときだけ、k6 の load シナリオを裏で動かす
import { spawn, execFileSync } from 'node:child_process'
import { createWriteStream, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { FullConfig } from '@playwright/test'
import { warmUp } from './warmup.ts'

const CONTAINER = 'e2e-k6-load'
// k6 の load シナリオが 100 VU に達するまでの時間（perf/timeline.ts の ramp-up 1 分）に少し足したもの
const RAMP_UP_MS = 65_000
const SETUP_TIMEOUT_MS = 10 * 60_000

export default async function globalSetup(config: FullConfig): Promise<(() => Promise<void>) | undefined> {
  const baseURL = config.projects[0].use.baseURL ?? 'http://localhost:5173'
  console.log('バックエンドを温めています…')
  await warmUp(baseURL)
  if (!process.env.E2E_LOAD) return undefined

  const repoRoot = fileURLToPath(new URL('../..', import.meta.url))
  mkdirSync('perf-results', { recursive: true })
  const log = createWriteStream('perf-results/k6-load.log')
  // 前回のコンテナが残っていたら消す
  try {
    execFileSync('docker', ['rm', '-f', CONTAINER], { stdio: 'ignore' })
  } catch {
    // 無ければよい
  }

  console.log('k6 の負荷（100 VU）をかけ始めます。テストデータの作成と ramp-up で 2〜3 分かかります…')
  const k6 = spawn(
    'docker',
    [
      'compose', '--profile', 'perf', 'run', '--rm', '-T', '--name', CONTAINER,
      // テストの間ずっと 100 VU を保つ（終わったら teardown で止める）。
      // k6 のアクセストークンは 15 分で切れるので、setup + ramp-up と合わせて 15 分以内に収める
      '-e', 'SCENARIO=load', '-e', 'HOLD=12m',
      'k6', 'run', '/perf/timeline.ts',
    ],
    // MSYS_NO_PATHCONV: Git Bash から実行しても /perf/... が Windows のパスに書き換えられないようにする
    { cwd: repoRoot, env: { ...process.env, MSYS_NO_PATHCONV: '1' } },
  )
  k6.stdout.pipe(log)
  k6.stderr.pipe(log)

  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('k6 の setup が時間内に終わりませんでした')), SETUP_TIMEOUT_MS)
    const onData = (chunk: Buffer) => {
      if (chunk.toString().includes('setup: runId=')) {
        clearTimeout(timer)
        resolve()
      }
    }
    k6.stdout.on('data', onData)
    k6.stderr.on('data', onData)
    k6.on('exit', (code) => {
      clearTimeout(timer)
      reject(new Error(`k6 が途中で終了しました (exit ${code})。perf-results/k6-load.log を確認してください`))
    })
  })
  console.log('k6 の setup が終わりました。100 VU に達するまで待ちます…')
  await new Promise((resolve) => setTimeout(resolve, RAMP_UP_MS))
  console.log('100 VU の負荷中にテストを始めます')

  return async () => {
    // k6 は止められると、それまでの要約を出して終わる
    const exited = new Promise((resolve) => k6.once('exit', resolve))
    try {
      execFileSync('docker', ['stop', CONTAINER], { stdio: 'ignore' })
    } catch {
      // すでに止まっていればよい
    }
    await exited
    log.end()
    console.log('k6 を止めました（ログ: perf-results/k6-load.log）')
  }
}
