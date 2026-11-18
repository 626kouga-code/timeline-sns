// F-30 いいね・取り消し / F-31 コメント / F-32 返信 / F-33 コメント削除
import { expect, test } from '../support/fixtures.ts'

test('いいねすると件数が増え、取り消すと戻る（F-30）', async ({ page, api, newUser, loginAs }) => {
  const author = await newUser()
  const me = await newUser()
  const post = await api.createPost(author, `いいねされる投稿 ${author.handle}`)
  await loginAs(me)
  // 全体タイムラインは並列で動く他のテストの投稿で埋まることがあるので、投稿者のプロフィールで操作する
  await page.goto(`/users/${author.handle}`)

  const card = page.getByRole('article').filter({ hasText: post.body })
  // ボタンは通信の完了を待たずに切り替わる（楽観的更新）ので、応答を待ってから再読み込みする
  const liked = page.waitForResponse(
    (res) => res.request().method() === 'POST' && res.url().endsWith(`/api/posts/${post.id}/likes`),
  )
  await card.getByRole('button', { name: 'いいね（0 件）' }).click()
  await expect(card.getByRole('button', { name: 'いいねを取り消す（1 件）' })).toBeVisible()
  expect((await liked).ok()).toBeTruthy()

  // 再読み込みしても保存されている
  await page.reload()
  await card.getByRole('button', { name: 'いいねを取り消す（1 件）' }).click()
  await expect(card.getByRole('button', { name: 'いいね（0 件）' })).toBeVisible()
})

test('投稿詳細でコメントし、そのコメントに返信できる（F-31 / F-32）', async ({ page, api, newUser, loginAs }) => {
  const author = await newUser()
  const me = await newUser()
  const post = await api.createPost(author, `コメントされる投稿 ${author.handle}`)
  await loginAs(me)
  await page.goto(`/posts/${post.id}`)

  await page.getByLabel('コメントの本文').fill('最初のコメント')
  await page.getByRole('button', { name: 'コメント', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'コメントしました' })).toBeVisible()
  await expect(page.getByText('最初のコメント')).toBeVisible()

  await page.getByRole('button', { name: '返信', exact: true }).first().click()
  await page.getByLabel('返信の本文').fill('コメントへの返信')
  await page.getByRole('button', { name: '返信', exact: true }).last().click()
  await expect(page.getByRole('status').filter({ hasText: '返信しました' })).toBeVisible()
  await expect(page.getByText('コメントへの返信')).toBeVisible()
  await expect(page.getByText('2 コメント')).toBeVisible()
})

test('自分のコメントを削除すると「削除されました」になる（F-33）', async ({ page, api, newUser, loginAs }) => {
  const author = await newUser()
  const me = await newUser()
  const post = await api.createPost(author, `コメントを消す投稿 ${author.handle}`)
  const comment = await api.comment(me, post, '消す予定のコメント')
  // 返信が付いているコメントは、消しても「削除されました」として残る
  await api.comment(author, post, '残る返信', comment.id)
  await loginAs(me)
  await page.goto(`/posts/${post.id}`)
  await expect(page.getByText('消す予定のコメント')).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'コメントのメニュー' }).first().click()
  await page.getByRole('button', { name: '削除' }).click()

  await expect(page.getByText('このコメントは削除されました')).toBeVisible()
  await expect(page.getByText('消す予定のコメント')).toHaveCount(0)
  await expect(page.getByText('残る返信')).toBeVisible()
})
