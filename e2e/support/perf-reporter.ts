// ブラウザ性能の計測値（support/perf.ts がテストに添付したもの）を集め、
// 実行の最後にコンソールへ表を出して perf-results/browser-perf.json に保存するレポーター。
import { mkdirSync, writeFileSync } from 'node:fs'
import type { Reporter, TestCase, TestResult } from '@playwright/test/reporter'
import { PERF_ATTACHMENT, type PerfResult } from './perf.ts'

export default class PerfReporter implements Reporter {
  private readonly results: (PerfResult & { test: string; status: string })[] = []

  onTestEnd(test: TestCase, result: TestResult): void {
    for (const attachment of result.attachments) {
      if (attachment.name !== PERF_ATTACHMENT || !attachment.body) continue
      const perf = JSON.parse(attachment.body.toString()) as PerfResult
      this.results.push({ ...perf, test: test.title, status: result.status })
    }
  }

  onEnd(): void {
    if (this.results.length === 0) return
    const underLoad = this.results.some((r) => r.underLoad)
    const ms = (v: number | null) => (v == null ? '-' : `${v}ms`)
    console.log(`\nブラウザ性能（${underLoad ? 'k6 の負荷中' : '負荷なし'}）`)
    console.table(
      this.results.map((r) => ({
        計測: r.name,
        表示時間: ms(r.ms),
        FCP: ms(r.fcp),
        LCP: ms(r.lcp),
        上限: ms(r.budget),
        判定: r.ms < r.budget ? 'OK' : 'NG',
      })),
    )
    mkdirSync('perf-results', { recursive: true })
    writeFileSync(
      `perf-results/browser-perf${underLoad ? '-load' : ''}.json`,
      JSON.stringify({ measuredAt: new Date().toISOString(), underLoad, results: this.results }, null, 2),
    )
  }

  printsToStdio(): boolean {
    return false
  }
}
