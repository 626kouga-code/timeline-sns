// アクセシビリティの自動チェック（axe-core）。WCAG 2.1 の A・AA の基準で検査する。
import AxeBuilder from '@axe-core/playwright'
import { expect, type Page, type TestInfo } from '@playwright/test'

// 既存の違反として分かっていて、#73 で直すルール。直したらここから外す。
// ここに無いルールの違反は、すべて失敗になる（新しく増えた違反は見逃さない）
const KNOWN_VIOLATIONS = [
  'color-contrast', // 薄いグレーの補足テキスト・水色のリンクなど、文字と背景のコントラスト不足
  'link-name', // アイコン画像だけのリンクに名前がない
  'label-content-name-mismatch', // いいねボタン・通知リンクの見た目の文字と読み上げ用の名前の食い違い
]

export async function expectAccessible(page: Page, testInfo: TestInfo, screen: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .disableRules(KNOWN_VIOLATIONS)
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
