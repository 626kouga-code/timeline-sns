// ブラウザ性能の計測（docs/08 の「ブラウザ性能」）。
// 要件（docs/03）: タイムラインの初回表示は 2 秒以内（100 ユーザー同時接続時）
import { expect, type Locator, type Page, type TestInfo } from '@playwright/test'

/** 表示時間の上限（ミリ秒）。PERF_BUDGET_MS で変えられる。 */
export const PERF_BUDGET_MS = Number(process.env.PERF_BUDGET_MS || 2000)

export interface PerfResult {
  name: string
  /** 操作の開始から、指定した要素が見えるまで（ミリ秒） */
  ms: number
  /** ページを読み込んだときの First Contentful Paint / Largest Contentful Paint（ミリ秒）。画面遷移だけのときは null */
  fcp: number | null
  lcp: number | null
  budget: number
  /** k6 の負荷をかけながら計ったか */
  underLoad: boolean
}

export const PERF_ATTACHMENT = 'perf'

declare global {
  interface Window {
    __vitals?: { fcp?: number; lcp?: number }
  }
}

/** ページを読み込むたびに FCP・LCP を記録する仕掛けを入れる（fixtures の vitals から呼ぶ）。 */
export async function installVitals(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const vitals: { fcp?: number; lcp?: number } = {}
    window.__vitals = vitals
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (entry.name === 'first-contentful-paint') vitals.fcp = entry.startTime
      }
    }).observe({ type: 'paint', buffered: true })
    new PerformanceObserver((list) => {
      const entries = list.getEntries()
      vitals.lcp = entries[entries.length - 1].startTime
    }).observe({ type: 'largest-contentful-paint', buffered: true })
  })
}

/** 表示時間を判定するプロジェクト（playwright.config.ts）。録画なしで 1 件ずつ流す */
export const PERF_PROJECT = 'perf'

/**
 * action を始めてから ready が見えるまでの時間を計り、レポートに添付して、上限を超えていたら失敗にする。
 * navigation: action がページの読み込み（page.goto）なら true。FCP・LCP も記録する。
 *
 * e2e プロジェクト（動画・トレースを記録中で、時間が正しく計れない）では、操作して ready を待つだけにする。
 */
export async function measure(
  page: Page,
  testInfo: TestInfo,
  name: string,
  action: () => Promise<unknown>,
  ready: Locator,
  { navigation = false } = {},
): Promise<PerfResult | null> {
  const start = performance.now()
  await action()
  await ready.waitFor({ state: 'visible', timeout: 15_000 })
  const ms = Math.round(performance.now() - start)
  if (testInfo.project.name !== PERF_PROJECT) return null

  const vitals = navigation ? await page.evaluate(() => window.__vitals ?? {}) : {}
  const result: PerfResult = {
    name,
    ms,
    fcp: vitals.fcp != null ? Math.round(vitals.fcp) : null,
    lcp: vitals.lcp != null ? Math.round(vitals.lcp) : null,
    budget: PERF_BUDGET_MS,
    underLoad: !!process.env.E2E_LOAD,
  }
  await testInfo.attach(PERF_ATTACHMENT, { body: JSON.stringify(result), contentType: 'application/json' })
  expect(ms, `${name} の表示に ${ms}ms かかりました（上限 ${PERF_BUDGET_MS}ms）`).toBeLessThan(PERF_BUDGET_MS)
  return result
}
