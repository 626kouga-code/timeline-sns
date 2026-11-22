// F-50 通知一覧 / F-51 未読バッジ
import { expect, test } from '../support/fixtures.ts'

test('いいね・コメント・フォローされると未読の件数が出て、通知一覧を開くと既読になる（F-50 / F-51）', async ({
  page,
  api,
  newUser,
  loginAs,
}) => {
  const me = await newUser()
  const other = await newUser('通知を送る人')
  const post = await api.createPost(me, `通知が来る投稿 ${me.handle}`)
  await api.like(other, post)
  await api.comment(other, post, 'いい投稿ですね')
  await api.follow(other, me)
  await loginAs(me)

  await page.goto('/home')
  await expect(page.getByRole('link', { name: '通知 3 件の未読' })).toBeVisible()

  await page.getByRole('link', { name: '通知 3 件の未読' }).click()
  await expect(page).toHaveURL(/\/notifications$/)
  await expect(page.getByText('があなたの投稿にいいねしました')).toBeVisible()
  await expect(page.getByText(/があなたの投稿にコメントしました/)).toBeVisible()
  await expect(page.getByText('にフォローされました')).toBeVisible()

  // 開いたので既読になり、バッジが消える
  await expect(page.getByRole('link', { name: '通知', exact: true })).toBeVisible()
})
