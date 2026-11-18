// F-20 ホームタイムライン / F-21 全体タイムライン / F-22 新着表示
import { expect, test } from '../support/fixtures.ts'
import { measure } from '../support/perf.ts'

test('ホームにはフォローした人と自分の投稿だけが出る（F-20）@perf', async ({ page, api, newUser, loginAs }, testInfo) => {
  const me = await newUser()
  const followed = await newUser('フォローする人')
  const stranger = await newUser('知らない人')
  await api.follow(me, followed)
  const mine = await api.createPost(me, `自分の投稿 ${me.handle}`)
  const theirs = await api.createPost(followed, `フォロー中の人の投稿 ${followed.handle}`)
  const other = await api.createPost(stranger, `フォローしていない人の投稿 ${stranger.handle}`)
  await loginAs(me)

  // 要件（docs/03）の対象: ページを開いてから（ログイン状態の復元を含め）タイムラインが表示されるまで
  await measure(
    page,
    testInfo,
    'home_timeline',
    () => page.goto('/home'),
    page.getByRole('article').first(),
    { navigation: true },
  )
  await expect(page.getByRole('article').filter({ hasText: mine.body })).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: theirs.body })).toBeVisible()
  await expect(page.getByText(other.body)).toHaveCount(0)
  // 新しい順
  await expect(page.getByRole('article').first()).toContainText(theirs.body)
})

test('ゲストでも全体タイムラインが見られる（F-21）@perf', async ({ page, api, newUser }, testInfo) => {
  const author = await newUser()
  const post = await api.createPost(author, `全体に出る投稿 ${author.handle}`)

  await measure(
    page,
    testInfo,
    'global_timeline_guest',
    () => page.goto('/'),
    page.getByRole('article').first(),
    { navigation: true },
  )
  await expect(page.getByRole('heading', { name: '全体タイムライン' })).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: post.body })).toBeVisible()
  // ゲスト向けの登録・ログインの案内
  await expect(page.getByRole('link', { name: 'ログイン' }).first()).toBeVisible()
})

test('表示中に新しい投稿があると、30 秒後にお知らせが出て、押すと先頭に表示される（F-22）', async ({
  page,
  api,
  newUser,
  loginAs,
}) => {
  const me = await newUser()
  const followed = await newUser()
  await api.follow(me, followed)
  await api.createPost(followed, `前からある投稿 ${followed.handle}`)
  await loginAs(me)

  // 30 秒ごとの確認を待たずに済むよう、ページの時計を早送りできるようにする
  await page.clock.install()
  await page.goto('/home')
  await expect(page.getByRole('article')).toHaveCount(1)

  const fresh = await api.createPost(followed, `新しい投稿 ${followed.handle}`)
  await page.clock.runFor('00:31')
  const banner = page.getByRole('button', { name: '新しい投稿があります（1 件）' })
  await expect(banner).toBeVisible()
  await banner.click()
  await expect(page.getByRole('article').first()).toContainText(fresh.body)
})
