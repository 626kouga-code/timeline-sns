import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Comment } from '../api/types'
import { deferred, json, mockApi, renderWithProviders, request, user } from '../test/utils'
import { CommentForm, CommentThread } from './CommentThread'

const taroAuthor = { id: user.id, handle: user.handle, displayName: user.displayName, avatarUrl: null }
const hanakoAuthor = { id: 'u2', handle: 'hanako', displayName: '花子', avatarUrl: null }
const jiroAuthor = { id: 'u3', handle: 'jiro', displayName: '次郎', avatarUrl: null }

const comment = (id: string, parentId: string | null, author = hanakoAuthor, body = `${id} の本文`): Comment => ({
  id,
  parentId,
  body,
  createdAt: new Date().toISOString(),
  author,
  deleted: false,
})

const deleted = (id: string, parentId: string | null): Comment => ({
  ...comment(id, parentId),
  body: null,
  author: null,
  deleted: true,
})

describe('CommentThread', () => {
  it('読み込み中・空・エラーを出し分ける', async () => {
    const response = deferred<Response>()
    mockApi({ '/api/posts/p1/comments': () => response.promise })
    renderWithProviders(<CommentThread postId="p1" />)

    expect(screen.getByText('コメントを読み込み中…')).toBeInTheDocument()
    response.resolve(json(200, []))
    expect(await screen.findByText('まだコメントはありません')).toBeInTheDocument()
  })

  it('取得に失敗したら理由を出す', async () => {
    mockApi({
      '/api/posts/p1/comments': () => json(404, { code: 'POST_NOT_FOUND', detail: 'この投稿は存在しないか、表示できません' }),
    })
    renderWithProviders(<CommentThread postId="p1" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('この投稿は存在しないか、表示できません')
  })

  it('スレッドごとにまとめ、深い返信には誰への返信かを付ける', async () => {
    mockApi({
      '/api/posts/p1/comments': () =>
        json(200, [
          comment('a', null),
          comment('a1', 'a', jiroAuthor),
          comment('a1x', 'a1', taroAuthor),
          comment('b', null, jiroAuthor),
        ]),
    })
    renderWithProviders(<CommentThread postId="p1" />)

    expect(await screen.findByText('a の本文')).toBeInTheDocument()
    expect(screen.getByText('b の本文')).toBeInTheDocument()
    // 2 階層目（a への返信）には返信先を付けない
    const a1 = screen.getByText('a1 の本文').parentElement!
    expect(within(a1).queryByText(/への返信/)).not.toBeInTheDocument()
    // 3 階層目は 2 階層目に並べ、返信先（次郎）を付ける
    const a1x = screen.getByText('a1x の本文').parentElement!
    expect(within(a1x).getByRole('link', { name: '@jiro' })).toHaveAttribute('href', '/users/jiro')
    expect(a1x).toHaveTextContent('への返信')
  })

  it('削除済みのコメントは本文を出さず、その返信には「削除されたコメントへの返信」と付ける', async () => {
    mockApi({
      '/api/posts/p1/comments': () => json(200, [comment('a', null), deleted('a1', 'a'), comment('a1x', 'a1', jiroAuthor)]),
    })
    renderWithProviders(<CommentThread postId="p1" />)

    expect(await screen.findByText('このコメントは削除されました')).toBeInTheDocument()
    expect(screen.getByText('a1x の本文').parentElement).toHaveTextContent('削除されたコメントへの返信')
  })

  it('自分のコメントだけメニューから削除でき、削除したら取り直す', async () => {
    const fetchMock = mockApi({
      '/api/posts/p1/comments': () => json(200, [comment('a', null, taroAuthor), comment('b', null)]),
      '/api/comments/a': () => new Response(null, { status: 204 }),
    })
    vi.stubGlobal('confirm', () => true)
    renderWithProviders(<CommentThread postId="p1" />, { me: user })

    const menus = await screen.findAllByRole('button', { name: 'コメントのメニュー' })
    expect(menus).toHaveLength(1)
    await userEvent.click(menus[0])
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(await screen.findByRole('status')).toHaveTextContent('コメントを削除しました')
    expect(fetchMock.mock.calls.map(([path, init]) => `${init?.method ?? 'GET'} ${path}`)).toEqual([
      'GET /api/posts/p1/comments',
      'DELETE /api/comments/a',
      'GET /api/posts/p1/comments',
    ])
  })

  it('削除の確認でキャンセルしたら削除しない', async () => {
    const fetchMock = mockApi({ '/api/posts/p1/comments': () => json(200, [comment('a', null, taroAuthor)]) })
    vi.stubGlobal('confirm', () => false)
    renderWithProviders(<CommentThread postId="p1" />, { me: user })

    await userEvent.click(await screen.findByRole('button', { name: 'コメントのメニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('返信ボタンで返信欄を開き、返信先を付けて送る', async () => {
    const fetchMock = mockApi({
      '/api/posts/p1/comments': (_path, init) =>
        init?.method === 'POST' ? json(201, comment('r', 'a', taroAuthor)) : json(200, [comment('a', null)]),
    })
    renderWithProviders(<CommentThread postId="p1" />, { me: user })

    await userEvent.click(await screen.findByRole('button', { name: '返信' }))
    const box = screen.getByRole('textbox', { name: '返信の本文' })
    expect(box).toHaveAttribute('placeholder', '@hanako に返信')
    await userEvent.type(box, 'ありがとう')
    await userEvent.click(within(box.closest('form')!).getByRole('button', { name: '返信' }))

    expect(await screen.findByRole('status')).toHaveTextContent('返信しました')
    expect(JSON.parse(request(fetchMock, 1).body as string)).toEqual({ body: 'ありがとう', parentId: 'a' })
    expect(screen.queryByRole('textbox', { name: '返信の本文' })).not.toBeInTheDocument()
  })

  it('未ログインで返信を押すとログイン画面へ誘導する', async () => {
    mockApi({ '/api/posts/p1/comments': () => json(200, [comment('a', null)]) })
    renderWithProviders(<CommentThread postId="p1" />, { path: '/posts/p1' })

    await userEvent.click(await screen.findByRole('button', { name: '返信' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })
})

describe('CommentForm', () => {
  const setup = () => {
    renderWithProviders(<CommentForm postId="p1" parentId={null} placeholder="コメントする" />, { me: user })
    return {
      box: screen.getByRole('textbox', { name: 'コメントの本文' }),
      submit: screen.getByRole('button', { name: 'コメント' }),
    }
  }

  it('未ログインなら出さない', () => {
    renderWithProviders(<CommentForm postId="p1" parentId={null} placeholder="コメントする" />)

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('文字数を数え、空・空白だけ・280 文字超は送れない', async () => {
    const { box, submit } = setup()
    expect(screen.getByText('0/280')).toBeInTheDocument()
    expect(submit).toBeDisabled()

    await userEvent.type(box, '  ')
    expect(submit).toBeDisabled()

    await userEvent.clear(box)
    await userEvent.click(box)
    await userEvent.paste('あ'.repeat(281))
    expect(screen.getByText('281/280')).toBeInTheDocument()
    expect(submit).toBeDisabled()
  })

  it('前後の空白を除いて送り、送った時点で入力欄を空ける', async () => {
    const response = deferred<Response>()
    const fetchMock = mockApi({ '/api/posts/p1/comments': () => response.promise })
    const { box, submit } = setup()

    await userEvent.type(box, '  いいですね  ')
    await userEvent.click(submit)

    expect(box).toHaveValue('')
    expect(JSON.parse(request(fetchMock).body as string)).toEqual({ body: 'いいですね', parentId: null })
    response.resolve(json(201, comment('c', null, taroAuthor)))
    expect(await screen.findByRole('status')).toHaveTextContent('コメントしました')
  })

  it('失敗したら送った本文を戻し、理由を出す', async () => {
    mockApi({
      '/api/posts/p1/comments': () =>
        json(400, { code: 'VALIDATION_FAILED', detail: '入力内容に誤りがあります', errors: { body: '本文を入力してください' } }),
    })
    const { box, submit } = setup()

    await userEvent.type(box, 'いいですね')
    await userEvent.click(submit)

    expect(await screen.findByRole('alert')).toHaveTextContent('本文を入力してください')
    expect(box).toHaveValue('いいですね')
  })
})
