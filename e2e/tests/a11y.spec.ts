// 主な画面のアクセシビリティ（axe-core、WCAG 2.1 A・AA）。データを入れた状態で検査する
import { expectAccessible } from '../support/a11y.ts'
import { expect, test } from '../support/fixtures.ts'

test.describe('アクセシビリティ @a11y', () => {
  test('ゲストの画面（ログイン・登録・全体タイムライン・検索）', async ({ page, api, newUser }, testInfo) => {
    const author = await newUser('けんさく対象')
    await api.createPost(author, 'ゲストに見える投稿')

    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible()
    await expectAccessible(page, testInfo, 'ログイン')

    await page.goto('/signup')
    await expect(page.getByRole('heading', { name: 'アカウントを作成' })).toBeVisible()
    await expectAccessible(page, testInfo, '登録')

    await page.goto('/')
    await expect(page.getByRole('article').first()).toBeVisible()
    await expectAccessible(page, testInfo, '全体タイムライン（ゲスト）')

    await page.goto('/search')
    await page.getByLabel('ユーザーID・表示名で検索').fill(author.handle)
    await expect(page.getByRole('main').getByText(`@${author.handle}`)).toBeVisible()
    await expectAccessible(page, testInfo, '検索結果')
  })

  test('ログイン後の画面（ホーム・投稿モーダル・投稿詳細・プロフィール・編集・通知）', async ({
    page,
    api,
    newUser,
    loginAs,
  }, testInfo) => {
    const me = await newUser('わたし')
    const other = await newUser('ほかの人')
    await api.follow(me, other)
    const post = await api.createPost(other, 'コメント付きの投稿')
    const comment = await api.comment(me, post, 'コメントです')
    await api.comment(other, post, '返信です', comment.id)
    const myPost = await api.createPost(me, '自分の投稿')
    await api.like(other, myPost)
    await loginAs(me)

    await page.goto('/home')
    await expect(page.getByRole('article').first()).toBeVisible()
    await expectAccessible(page, testInfo, 'ホームタイムライン')

    await page.getByRole('button', { name: '投稿する' }).first().click()
    await expect(page.getByRole('dialog', { name: '投稿する' })).toBeVisible()
    await expectAccessible(page, testInfo, '投稿モーダル')
    await page.keyboard.press('Escape')

    await page.goto(`/posts/${post.id}`)
    await expect(page.getByText('返信です')).toBeVisible()
    await expectAccessible(page, testInfo, '投稿詳細')

    await page.goto(`/users/${other.handle}`)
    await expect(page.getByRole('article').first()).toBeVisible()
    await expectAccessible(page, testInfo, 'プロフィール')

    await page.goto('/settings/profile')
    await expect(page.getByRole('heading', { name: 'プロフィールを編集' })).toBeVisible()
    await expectAccessible(page, testInfo, 'プロフィール編集')

    await page.goto('/notifications')
    await expect(page.getByText('があなたの投稿にいいねしました')).toBeVisible()
    await expectAccessible(page, testInfo, '通知一覧')
  })
})
