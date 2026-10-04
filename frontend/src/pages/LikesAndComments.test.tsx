import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Comment } from '../api/types'
import { json, mockApi, page, post, renderApp, session, user } from '../test/utils'

const hanako = { id: 'u2', handle: 'hanako', displayName: '花子' }

function comment(id: string, parentId: string | null, author: Comment['author'] = hanako): Comment {
  return { id, parentId, body: `本文 ${id}`, createdAt: new Date().toISOString(), author, deleted: false }
}

/** 応答を手動で返せる Promise（楽観的更新の途中の表示を確かめる） */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

describe('いいね', () => {
  it('押した瞬間に表示が変わり、応答の数で確定する', async () => {
    const response = deferred<Response>()
    mockApi({
      '/api/auth/refresh': session,
      '/api/timeline/home': () => page([post('p1', 'いいねする投稿', hanako)]),
      '/api/posts/p1/likes': () => response.promise,
    })
    renderApp('/home')

    const button = await screen.findByRole('button', { name: 'いいね（0 件）' })
    await userEvent.click(button)

    // 応答を待たずに反映（楽観的更新）
    expect(button).toHaveAttribute('aria-pressed', 'true')
    expect(button).toHaveTextContent('1')

    // 他の人のいいねも含んだサーバーの数で確定
    response.resolve(json(200, { liked: true, likeCount: 5 }))
    expect(await screen.findByRole('button', { name: 'いいねを取り消す（5 件）' })).toBeInTheDocument()
  })

  it('失敗したら元に戻してメッセージを出す', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/timeline/home': () => page([post('p1', '消えた投稿', hanako)]),
      '/api/posts/p1/likes': () =>
        json(404, { code: 'POST_NOT_FOUND', detail: 'この投稿は存在しないか、表示できません' }),
    })
    renderApp('/home')

    await userEvent.click(await screen.findByRole('button', { name: 'いいね（0 件）' }))

    expect(await screen.findByText('この投稿は存在しないか、表示できません')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'いいね（0 件）' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('未ログインで押すとログイン画面へ移る', async () => {
    const fetchMock = mockApi({ '/api/timeline/global': () => page([post('p1', 'ゲストが見る投稿', hanako)]) })
    renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'いいね（0 件）' }))

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => String(path).includes('/likes'))).toBe(false)
  })
})

describe('コメント', () => {
  it('2 階層目より深い返信は「@ユーザー への返信」を付けて 2 階層目に並べる', async () => {
    mockApi({
      '/api/posts/p1': () => json(200, post('p1', '投稿', hanako)),
      '/api/posts/p1/comments': () =>
        json(200, [
          comment('c1', null),
          comment('c2', 'c1', { id: 'u3', handle: 'jiro', displayName: '次郎' }),
          comment('c3', 'c2'),
          { ...comment('c4', null), body: null, author: null, deleted: true },
          comment('c5', 'c4'),
        ]),
    })
    renderApp('/posts/p1')

    expect(await screen.findByText('本文 c3')).toBeInTheDocument()
    // c2 はルート直下なので返信先を出さない。c3 は c2（jiro）への返信
    expect(screen.getAllByText(/への返信/)).toHaveLength(1)
    expect(screen.getByRole('link', { name: '@jiro' })).toBeInTheDocument()
    expect(screen.getByText('このコメントは削除されました')).toBeInTheDocument()
    expect(screen.getByText('本文 c5')).toBeInTheDocument()
  })

  it('自分のコメントにだけ削除メニューが出る', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/posts/p1': () => json(200, post('p1', '投稿', hanako)),
      '/api/posts/p1/comments': () => json(200, [comment('mine', null, user), comment('theirs', null)]),
    })
    renderApp('/posts/p1')

    await screen.findByText('本文 theirs')
    expect(screen.getAllByRole('button', { name: 'コメントのメニュー' })).toHaveLength(1)
  })

  it('送信に失敗したら本文を入力欄に戻してエラーを出す', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/posts/p1': () => json(200, post('p1', '投稿', hanako)),
      '/api/posts/p1/comments': (_path, init) =>
        init?.method === 'POST'
          ? json(400, {
              code: 'VALIDATION_FAILED',
              detail: '入力内容に誤りがあります',
              errors: { body: '本文を入力してください' },
            })
          : json(200, []),
    })
    renderApp('/posts/p1')

    const box = await screen.findByLabelText('コメントの本文')
    await userEvent.type(box, '消えないでほしい')
    await userEvent.click(screen.getByRole('button', { name: 'コメント' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('本文を入力してください')
    expect(box).toHaveValue('消えないでほしい')
  })

  it('コメントと返信を送ると、送った内容でコメント欄を取り直す', async () => {
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/posts/p1': () => json(200, post('p1', '投稿', hanako)),
      '/api/posts/p1/comments': (_path, init) =>
        init?.method === 'POST' ? json(201, comment('new', null, user)) : json(200, [comment('c1', null)]),
    })
    renderApp('/posts/p1')

    await userEvent.type(await screen.findByLabelText('コメントの本文'), '  はじめまして  ')
    await userEvent.click(screen.getByRole('button', { name: 'コメント' }))
    expect(await screen.findByText('コメントしました')).toBeInTheDocument()

    await userEvent.click(within(screen.getByText('本文 c1').parentElement!).getByRole('button', { name: '返信' }))
    const replyBox = screen.getByLabelText('返信の本文')
    await userEvent.type(replyBox, 'よろしく')
    await userEvent.click(within(replyBox.closest('form')!).getByRole('button', { name: '返信' }))
    expect(await screen.findByText('返信しました')).toBeInTheDocument()

    const posted = fetchMock.mock.calls
      .filter(([path, init]) => path === '/api/posts/p1/comments' && init?.method === 'POST')
      .map(([, init]) => JSON.parse(init!.body as string))
    expect(posted).toEqual([
      { body: 'はじめまして', parentId: null },
      { body: 'よろしく', parentId: 'c1' },
    ])
    // 初回 ＋ 送信ごとの取り直し 2 回。返信ごとに問い合わせることはない
    const lists = fetchMock.mock.calls.filter(([path, init]) => path === '/api/posts/p1/comments' && !init?.method)
    expect(lists).toHaveLength(3)
  })
})
