import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Notification } from '../api/types'
import { json, mockApi, page, post, renderApp, session } from '../test/utils'

const hanako = { id: 'u2', handle: 'hanako', displayName: '花子', avatarUrl: null }

function notification(id: string, type: Notification['type'], overrides: Partial<Notification> = {}): Notification {
  return {
    id,
    type,
    createdAt: new Date().toISOString(),
    unread: false,
    actor: hanako,
    post: type === 'FOLLOW' ? null : { id: 'p1', body: '太郎の投稿', thumbnailUrl: null },
    comment: type === 'COMMENT' || type === 'REPLY' ? { id: `c-${id}`, body: `コメント ${id}` } : null,
    ...overrides,
  }
}

describe('通知一覧', () => {
  it('種類ごとの文言と内容を新しい順に表示する', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/notifications': () =>
        page([
          notification('n4', 'FOLLOW'),
          notification('n3', 'REPLY'),
          notification('n2', 'COMMENT', { comment: { id: 'c2', body: null } }),
          notification('n1', 'LIKE', { post: { id: 'p1', body: '', thumbnailUrl: '/api/media/posts/p1/1-thumb.jpg' } }),
        ]),
    })
    renderApp('/notifications')

    const items = await screen.findAllByRole('listitem')
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('花子 さんにフォローされました'),
      expect.stringContaining('花子 さんがあなたのコメントに返信しました'),
      expect.stringContaining('花子 さんがあなたの投稿にコメントしました'),
      expect.stringContaining('花子 さんがあなたの投稿にいいねしました'),
    ])
    expect(items[1]).toHaveTextContent('コメント n3')
    expect(items[2]).toHaveTextContent('このコメントは削除されました')
    expect(items[3]).toHaveTextContent('（画像のみの投稿）')
    expect(items[3].querySelector('img[src="/api/media/posts/p1/1-thumb.jpg"]')).not.toBeNull()
  })

  it('開いたら表示した分までを既読にし、未読だった通知は強調表示のまま残す', async () => {
    let unread = 2
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/notifications': () =>
        page([notification('n2', 'LIKE', { unread: true }), notification('n1', 'FOLLOW', { unread: true })]),
      '/api/notifications/unread-count': () => json(200, { count: unread }),
      '/api/notifications/read': () => {
        unread = 0
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/notifications')

    expect(await screen.findAllByRole('img', { name: '未読' })).toHaveLength(2)
    // 既読にしたら、ナビの未読バッジが消える
    expect(await screen.findAllByRole('link', { name: '通知' })).not.toHaveLength(0)
    expect(screen.queryByRole('link', { name: /未読/ })).not.toBeInTheDocument()
    expect(screen.getAllByRole('img', { name: '未読' })).toHaveLength(2)

    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/notifications/read') ?? []
    expect(init).toMatchObject({ method: 'POST', body: JSON.stringify({ until: 'n2' }) })
  })

  it('すべて既読なら既読化を送らない', async () => {
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/notifications': () => page([notification('n1', 'FOLLOW')]),
    })
    renderApp('/notifications')

    await screen.findByText(/フォローされました/)
    expect(fetchMock.mock.calls.some(([path]) => path === '/api/notifications/read')).toBe(false)
  })

  it('通知がなければその旨を出す', async () => {
    mockApi({ '/api/auth/refresh': session, '/api/notifications': () => page([]) })
    renderApp('/notifications')

    expect(await screen.findByText('まだ通知はありません')).toBeInTheDocument()
  })

  it('投稿への反応は投稿へ、フォローは相手のプロフィールへ移る', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/notifications': () => page([notification('n2', 'FOLLOW'), notification('n1', 'LIKE')]),
      '/api/posts/p1': () => json(200, post('p1', '太郎の投稿')),
      '/api/users/hanako': () => json(404, { code: 'USER_NOT_FOUND', detail: 'x' }),
    })
    renderApp('/notifications')

    const items = await screen.findAllByRole('listitem')
    await userEvent.click(within(items[1]).getByText(/いいねしました/))
    expect(await screen.findByRole('heading', { name: '投稿' })).toBeInTheDocument()
  })
})

describe('未読バッジ', () => {
  it('未読の通知数をナビに出す', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/notifications/unread-count': () => json(200, { count: 3 }),
    })
    renderApp('/home')

    expect((await screen.findAllByRole('link', { name: '通知（未読 3 件）' })).length).toBeGreaterThan(0)
  })

  // サーバーは 100 件で数えるのをやめる
  it('100 件以上は 99+ にする', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/notifications/unread-count': () => json(200, { count: 100 }),
    })
    renderApp('/home')

    expect((await screen.findAllByRole('link', { name: '通知（未読 99+ 件）' })).length).toBeGreaterThan(0)
    expect(screen.getAllByText('99+').length).toBeGreaterThan(0)
  })

  it('ゲストには出さない', async () => {
    const fetchMock = mockApi({})
    renderApp('/')

    await screen.findByText('いま起きていることを見つけよう')
    expect(fetchMock.mock.calls.some(([path]) => String(path).includes('/api/notifications'))).toBe(false)
  })
})
