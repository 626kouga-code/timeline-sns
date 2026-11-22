// アクセシビリティの自動チェック（axe-core）。WCAG 2.1 の A・AA の基準で検査し、違反はすべて失敗にする。
import AxeBuilder from '@axe-core/playwright'
import { expect, type Page, type TestInfo } from '@playwright/test'

export async function expectAccessible(page: Page, testInfo: TestInfo, screen: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const violations = results.violations.map((v) => ({
    rule: v.id,
    impact: v.impact,
    help: v.help,
    targets: v.nodes.map((n) => n.target.join(' ')),
  }))
  if (violations.length > 0) {
    await testInfo.attach(`アクセシビリティ違反（${screen}）`, {
      body: JSON.stringify(violations, null, 2),
      contentType: 'application/json',
    })
  }
  // 1 画面で違反が見つかっても止めず、残りの画面も検査してからまとめて失敗にする
  expect.soft(violations, `${screen} にアクセシビリティの違反があります`).toEqual([])
}
