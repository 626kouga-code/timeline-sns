// F-10 テキスト投稿 / F-11 画像投稿 / F-12 投稿削除 / F-13 投稿詳細
import { IMAGE_PATH, imageFile } from '../support/api.ts'
import { expect, test } from '../support/fixtures.ts'
import { measure } from '../support/perf.ts'

test('投稿すると、ホームと全体タイムラインに出る（F-10）@perf', async ({ page, newUser, loginAs }, testInfo) => {
  const user = await newUser()
  await loginAs(user)
  await page.goto('/home')

  const body = `はじめての投稿 ${user.handle}`
  await page.getByRole('button', { name: '投稿する' }).first().click()
  const dialog = page.getByRole('dialog', { name: '投稿する' })
  await dialog.getByLabel('本文').fill(body)
  // 投稿ボタンを押してから、自分の投稿がタイムラインに出るまで
  await measure(
    page,
    testInfo,
    'post_to_timeline',
    () => dialog.getByRole('button', { name: '投稿する' }).click(),
    page.getByRole('article').filter({ hasText: body }),
  )
  await expect(page.getByRole('status').filter({ hasText: '投稿しました' })).toBeVisible()
  await expect(dialog).toBeHidden()

  await page.goto('/')
  await expect(page.getByRole('article').filter({ hasText: body })).toBeVisible()
})

test('画像付きで投稿すると、画像が表示される（F-11）', async ({ page, newUser, loginAs }) => {
  const user = await newUser()
  await loginAs(user)
  await page.goto('/home')

  await page.getByRole('button', { name: '投稿する' }).first().click()
  const dialog = page.getByRole('dialog', { name: '投稿する' })
  await dialog.getByLabel('本文').fill('画像のテスト')
  await dialog.getByTestId('compose-images').setInputFiles(IMAGE_PATH)
  await expect(dialog.getByRole('list', { name: '添付する画像' }).getByRole('listitem')).toHaveCount(1)
  await dialog.getByRole('button', { name: '投稿する' }).click()

  const post = page.getByRole('article').filter({ hasText: '画像のテスト' })
  const image = post.getByRole('button', { name: '画像 1 を拡大' }).locator('img')
  await expect(image).toBeVisible()
  // 画像が実際に読み込めている（サーバーで変換・保存され、/api/media から配信されている）
  await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0)
})

test('投稿をクリックすると詳細が開き、メニューから削除できる（F-13 / F-12）@perf', async ({
  page,
  api,
  newUser,
  loginAs,
}, testInfo) => {
  const user = await newUser()
  const post = await api.createPost(user, `消す予定の投稿 ${user.handle}`)
  await loginAs(user)
  await page.goto('/home')

  await measure(
    page,
    testInfo,
    'post_detail',
    () => page.getByRole('article').filter({ hasText: post.body }).getByText(post.body).click(),
    page.getByRole('heading', { name: '投稿' }),
  )
  await expect(page).toHaveURL(new RegExp(`/posts/${post.id}$`))
  await expect(page.getByText(post.body)).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '投稿のメニュー' }).click()
  await page.getByRole('button', { name: '削除' }).click()

  await page.goto('/home')
  await expect(page.getByText(post.body)).toHaveCount(0)
})


test('投稿の OGP を返し、人が開いたら投稿詳細へ移る（F-14）', async ({ page, request, api, newUser }) => {
  const user = await newUser('OGP の人')
  const post = await api.createPost(user, `OGP のテスト ${user.handle}`, imageFile())

  // SNS のクローラーとして取得する（本番では CloudFront が /posts/{id} をここへ回す）
  const res = await request.get(`/api/ogp/posts/${post.id}`, { headers: { 'User-Agent': 'Twitterbot/1.0' } })
  expect(res.status()).toBe(200)
  const html = await res.text()
  expect(html).toContain(`<meta property="og:title" content="OGP の人（@${user.handle}）">`)
  expect(html).toContain(`<meta property="og:description" content="OGP のテスト ${user.handle}">`)
  // 画像は絶対 URL で、外から読める
  const image = html.match(/<meta property="og:image" content="([^"]+)">/)?.[1]
  expect(image).toMatch(/^http:\/\/localhost:5173\//)
  expect((await request.get(image!)).headers()['content-type']).toMatch(/^image\//)

  await page.goto(`/api/ogp/posts/${post.id}`)
  await expect(page).toHaveURL(`/posts/${post.id}`)
  await expect(page.getByRole('article').filter({ hasText: `OGP のテスト ${user.handle}` })).toBeVisible()
})
