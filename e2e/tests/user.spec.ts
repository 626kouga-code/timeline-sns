// F-40 プロフィール表示 / F-41 プロフィール編集 / F-42 フォロー・解除 / F-43 フォロー・フォロワー一覧 / F-44 ユーザー検索
import { IMAGE_PATH } from '../support/api.ts'
import { expect, test } from '../support/fixtures.ts'
import { measure } from '../support/perf.ts'

test('プロフィールを開き、フォロー・解除できる。一覧にも反映される（F-40 / F-42 / F-43）@perf', async ({
  page,
  api,
  newUser,
  loginAs,
}, testInfo) => {
  const me = await newUser('わたし')
  const target = await newUser('フォローされる人')
  const post = await api.createPost(target, `プロフィールに出る投稿 ${target.handle}`)
  await loginAs(me)

  await measure(
    page,
    testInfo,
    'profile',
    () => page.goto(`/users/${target.handle}`),
    page.getByRole('article').filter({ hasText: post.body }),
    { navigation: true },
  )
  await expect(page.getByRole('heading', { level: 2, name: 'フォローされる人' })).toBeVisible()

  const followed = page.waitForResponse(
    (res) => res.request().method() === 'PUT' && res.url().endsWith(`/api/users/${target.handle}/follow`),
  )
  await page.getByRole('button', { name: `フォロー @${target.handle}` }).click()
  await expect(page.getByRole('button', { name: `フォロー中 @${target.handle}` })).toBeVisible()
  expect((await followed).ok()).toBeTruthy()

  // フォロワー一覧に自分が出る
  await page.getByRole('link', { name: /フォロワー/ }).click()
  await expect(page).toHaveURL(new RegExp(`/users/${target.handle}/followers$`))
  await expect(page.getByRole('main').getByRole('paragraph').filter({ hasText: `@${me.handle}` })).toBeVisible()

  // 自分のフォロー中一覧に相手が出る
  await page.goto(`/users/${me.handle}/following`)
  await expect(page.getByRole('main').getByRole('paragraph').filter({ hasText: `@${target.handle}` })).toBeVisible()

  // 解除すると、相手のフォロワー一覧から消える
  await page.goto(`/users/${target.handle}`)
  // ボタンは通信の完了を待たずに切り替わる（楽観的更新）ので、解除の応答を待ってから次の画面へ移る
  const unfollowed = page.waitForResponse(
    (res) => res.request().method() === 'DELETE' && res.url().endsWith(`/api/users/${target.handle}/follow`),
  )
  await page.getByRole('button', { name: `フォロー中 @${target.handle}` }).click()
  await expect(page.getByRole('button', { name: `フォロー @${target.handle}` })).toBeVisible()
  expect((await unfollowed).ok()).toBeTruthy()
  await page.goto(`/users/${target.handle}/followers`)
  await expect(page.getByRole('main').getByRole('paragraph').filter({ hasText: `@${me.handle}` })).toHaveCount(0)
})

test('表示名・自己紹介・アイコン画像を変更できる（F-41）', async ({ page, newUser, loginAs }) => {
  const me = await newUser('変更前の名前')
  await loginAs(me)
  await page.goto('/settings/profile')
  await expect(page.getByRole('heading', { name: 'プロフィールを編集' })).toBeVisible()

  // アイコンはその場で保存される（表示名・自己紹介の「保存する」とは別）
  await page.getByTestId('avatar-input').setInputFiles(IMAGE_PATH)
  const cropper = page.getByRole('dialog', { name: 'アイコン画像を切り抜く' })
  await expect(cropper).toBeVisible()
  await cropper.getByRole('button', { name: '適用' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'アイコン画像を変更しました' })).toBeVisible()

  // ラベルには文字数のカウンターも入るので、前方一致で探す
  await page.getByLabel(/^表示名/).fill('変更後の名前')
  await page.getByLabel(/^自己紹介/).fill('E2E テストで書いた自己紹介です')
  await page.getByRole('button', { name: '保存する' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'プロフィールを更新しました' })).toBeVisible()

  // 保存するとプロフィールに移る
  await expect(page).toHaveURL(new RegExp(`/users/${me.handle}$`))
  await expect(page.getByRole('heading', { level: 2, name: '変更後の名前' })).toBeVisible()
  await expect(page.getByText('E2E テストで書いた自己紹介です')).toBeVisible()
})

test('ユーザー ID や表示名の一部で検索できる（F-44）', async ({ page, newUser }) => {
  const target = await newUser('けんさくたろう')
  await page.goto('/search')

  await page.getByLabel('ユーザーID・表示名で検索').fill(target.handle)
  await expect(page.getByRole('main').getByRole('paragraph').filter({ hasText: `@${target.handle}` })).toBeVisible()

  await page.getByLabel('ユーザーID・表示名で検索').fill('けんさく')
  await expect(page.getByRole('main').getByRole('paragraph').filter({ hasText: `@${target.handle}` })).toBeVisible()
  await page.getByRole('main').getByRole('paragraph').filter({ hasText: `@${target.handle}` }).click()
  await expect(page).toHaveURL(new RegExp(`/users/${target.handle}$`))
})
