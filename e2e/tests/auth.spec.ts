// F-01 新規登録 / F-03 ログイン・ログアウト
import { uniqueHandle } from '../support/api.ts'
import { expect, test } from '../support/fixtures.ts'

test('画面から新規登録すると、ホームタイムラインに移る（F-01）', async ({ page }, testInfo) => {
  const handle = uniqueHandle(testInfo.workerIndex)
  await page.goto('/signup')
  await expect(page.getByRole('heading', { name: 'アカウントを作成' })).toBeVisible()

  await page.getByLabel('メールアドレス').fill(`${handle}@example.com`)
  await page.getByLabel('パスワード', { exact: true }).fill('e2e-password')
  await page.getByLabel('ユーザーID').fill(handle)
  await page.getByLabel('表示名').fill('新しいユーザー')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '登録する' }).click()

  await expect(page).toHaveURL(/\/home$/)
  await expect(page.getByRole('status').filter({ hasText: '登録しました' })).toBeVisible()
})

test('ログアウトして、画面からログインし直せる（F-03）', async ({ page, newUser, loginAs }) => {
  const user = await newUser()
  await loginAs(user)
  await page.goto('/home')
  await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()

  await page.getByRole('button', { name: 'ログアウト' }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('navigation').getByRole('link', { name: 'ログイン' })).toBeVisible()

  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill(user.password)
  await page.getByRole('button', { name: 'ログイン', exact: true }).click()
  await expect(page).toHaveURL(/\/home$/)
})

test('パスワードが違うとログインできず、エラーが出る（F-03）', async ({ page, newUser }) => {
  const user = await newUser()
  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill('wrong-password')
  await page.getByRole('button', { name: 'ログイン', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page).toHaveURL(/\/login$/)
})

test('ログインが必要な画面を開くとログイン画面に移り、ログイン後に元の画面へ戻る', async ({ page, newUser }) => {
  const user = await newUser()
  await page.goto('/notifications')
  await expect(page).toHaveURL(/\/login$/)

  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill(user.password)
  await page.getByRole('button', { name: 'ログイン', exact: true }).click()
  await expect(page).toHaveURL(/\/notifications$/)
})
