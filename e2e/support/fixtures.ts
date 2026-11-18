// すべてのテストで使うフィクスチャ。テストは `import { test, expect } from '../support/fixtures.ts'` で書く。
//
// - api / newUser / loginAs: 前提データの作成とログイン
// - errorGuard（自動）: ブラウザのエラー・API の 5xx を見張り、出たらテストを失敗にする
// - vitals（自動）: 表示時間の計測用に FCP・LCP を集める（support/perf.ts）
import { test as base, expect, type Page } from '@playwright/test'
import { Api, type User } from './api.ts'
import { installVitals } from './perf.ts'

interface Fixtures {
  api: Api
  /** 新しいユーザーを API で登録する（テストごとに別のユーザーを使う）。 */
  newUser: (displayName?: string) => Promise<User>
  /** そのユーザーでログインした状態にする（refresh Cookie がこのページのコンテキストに入る）。 */
  loginAs: (user: User, page?: Page) => Promise<void>
  /** 意図して起こすエラーの許可パターン。使うときは理由をコメントで書く。 */
  allowErrors: RegExp[]
  errorGuard: void
  vitals: void
}

// 4xx の fetch でも、ブラウザはコンソールに「Failed to load resource」を出す（未ログイン時の refresh 401 など、
// 正常な動き）。HTTP の失敗は response の 5xx だけで判定するので、この文言は除外する
const IGNORED_CONSOLE = [/Failed to load resource/]

export const test = base.extend<Fixtures>({
  api: async ({ playwright }, use, testInfo) => {
    // ページとは別の Cookie 置き場。他のユーザーとして操作しても、ページのログイン状態に影響しない
    const request = await playwright.request.newContext({ baseURL: testInfo.project.use.baseURL })
    await use(new Api(request, testInfo.workerIndex))
    await request.dispose()
  },

  newUser: async ({ api }, use) => {
    await use((displayName) => api.createUser(displayName))
  },

  loginAs: async ({ page }, use) => {
    await use(async (user, target = page) => {
      const res = await target.request.post('/api/auth/login', { data: { email: user.email, password: user.password } })
      expect(res.ok(), `ログインに失敗しました (${res.status()})`).toBeTruthy()
    })
  },

  allowErrors: [[], { option: true }],

  errorGuard: [
    async ({ page, allowErrors }, use, testInfo) => {
      const errors: string[] = []
      page.on('pageerror', (error) => errors.push(`JS の例外: ${error.message}`))
      page.on('console', (message) => {
        if (message.type() !== 'error') return
        if (IGNORED_CONSOLE.some((pattern) => pattern.test(message.text()))) return
        errors.push(`console.error: ${message.text()}`)
      })
      page.on('response', (response) => {
        if (response.status() >= 500) {
          errors.push(`HTTP ${response.status()}: ${response.request().method()} ${response.url()}`)
        }
      })

      await use()

      if (errors.length > 0) {
        await testInfo.attach('ブラウザのエラー', { body: errors.join('\n'), contentType: 'text/plain' })
      }
      const unexpected = errors.filter((error) => !allowErrors.some((pattern) => pattern.test(error)))
      expect(unexpected, 'ブラウザでエラーが発生しました（添付「ブラウザのエラー」を参照）').toEqual([])
    },
    { auto: true },
  ],

  vitals: [
    async ({ page }, use) => {
      await installVitals(page)
      await use()
    },
    { auto: true },
  ],
})

export { expect }
export type { User }
