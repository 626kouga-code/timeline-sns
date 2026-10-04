import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { json, mockApi, post, renderApp, session } from '../test/utils'

describe('投稿詳細', () => {
  it('本文・いいね数・コメント数を表示する', async () => {
    mockApi({
      '/api/posts/p1': () => json(200, { ...post('p1', '詳細の本文'), likeCount: 3, commentCount: 2 }),
    })
    renderApp('/posts/p1')

    expect(await screen.findByText('詳細の本文')).toBeInTheDocument()
    expect(screen.getByText('3').parentElement).toHaveTextContent('3 いいね')
    expect(screen.getByText('2').parentElement).toHaveTextContent('2 コメント')
    // ゲストには削除メニューを出さない
    expect(screen.queryByRole('button', { name: '投稿のメニュー' })).not.toBeInTheDocument()
  })

  it('存在しない投稿はメッセージを表示する', async () => {
    mockApi({
      '/api/posts/missing': () =>
        json(404, { code: 'POST_NOT_FOUND', detail: 'この投稿は存在しないか、表示できません' }),
    })
    renderApp('/posts/missing')

    expect(await screen.findByRole('alert')).toHaveTextContent('この投稿は存在しないか、表示できません')
  })

  it('404 以外の 4xx も取り直さない（1 回だけ問い合わせる）', async () => {
    const fetchMock = mockApi({
      '/api/posts/p1': () => json(400, { code: 'VALIDATION_FAILED', detail: '入力内容に誤りがあります' }),
    })
    renderApp('/posts/p1')

    expect(await screen.findByRole('alert')).toHaveTextContent('入力内容に誤りがあります')
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/posts/p1')).toHaveLength(1)
  })

  it('自分の投稿を削除するとホームへ移る', async () => {
    vi.stubGlobal('confirm', () => true)
    mockApi({
      '/api/auth/refresh': () => session(),
      '/api/posts/p1': (_path, init) =>
        init?.method === 'DELETE' ? new Response(null, { status: 204 }) : json(200, post('p1', '消す投稿')),
    })
    renderApp('/posts/p1')

    await userEvent.click(await screen.findByRole('button', { name: '投稿のメニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
    expect(screen.getByText('投稿を削除しました')).toBeInTheDocument()
  })
})
