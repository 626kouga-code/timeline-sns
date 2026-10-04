import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, mockApi, page, post, renderApp, session } from '../test/utils'
import { NEW_POSTS_INTERVAL } from './TimelinePage'

const hanako = { id: 'u2', handle: 'hanako', displayName: '花子' }

afterEach(() => {
  vi.useRealTimers()
})

describe('全体タイムライン', () => {
  it('ゲストにも投稿を新しい順に表示し、登録を案内する', async () => {
    mockApi({
      '/api/timeline/global': () => page([post('p2', '2 件目', hanako), post('p1', '1 件目')]),
    })
    renderApp('/')

    const articles = await screen.findAllByRole('article')
    expect(articles.map((a) => within(a).getByText(/件目/).textContent)).toEqual(['2 件目', '1 件目'])
    expect(screen.getByRole('link', { name: '新規登録' })).toBeInTheDocument()
    expect(screen.queryByText('いまどうしてる？')).not.toBeInTheDocument()
  })

  it('次のページを cursor つきで読み込む', async () => {
    const fetchMock = mockApi({
      '/api/timeline/global': () => page([post('p2', '新しい投稿')], 'p2'),
      '/api/timeline/global?cursor=p2': () => page([post('p1', '古い投稿')]),
    })
    renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'さらに読み込む' }))

    expect(await screen.findByText('古い投稿')).toBeInTheDocument()
    expect(screen.getByText('これ以上の投稿はありません')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/timeline/global?cursor=p2', expect.anything())
  })

  it('投稿がなければその旨を表示する', async () => {
    mockApi({})
    renderApp('/')

    expect(await screen.findByText('まだ投稿がありません')).toBeInTheDocument()
  })

  it('取得に失敗したらメッセージを表示する', async () => {
    mockApi({
      '/api/timeline/global': () => json(500, { code: 'INTERNAL', detail: 'サーバーでエラーが発生しました' }),
    })
    renderApp('/')

    expect(await screen.findByRole('alert', {}, { timeout: 3000 })).toHaveTextContent('サーバーでエラーが発生しました')
  })
})

describe('新着表示（F-22）', () => {
  it('30 秒ごとに新着を確認し、タップで先頭から取り直す', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let calls = 0
    const fetchMock = mockApi({
      '/api/timeline/global': () => {
        calls += 1
        return calls === 1 ? page([post('p1', '最初の投稿')]) : page([post('p3', '新しい投稿', hanako), post('p1', '最初の投稿')])
      },
      '/api/timeline/global/new-count?since=p1': () => json(200, { count: 2 }),
    })
    renderApp('/')
    await screen.findByText('最初の投稿')

    // 表示した直後には問い合わせない
    expect(fetchMock).not.toHaveBeenCalledWith('/api/timeline/global/new-count?since=p1', expect.anything())
    await act(() => vi.advanceTimersByTimeAsync(NEW_POSTS_INTERVAL))

    const banner = await screen.findByRole('button', { name: '新しい投稿があります（2 件）' })
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(banner)

    expect(await screen.findByText('新しい投稿')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /新しい投稿があります/ })).not.toBeInTheDocument()
  })
})

describe('ホームタイムラインと投稿', () => {
  it('本文を入力して投稿すると、タイムラインを取り直す', async () => {
    let posted = false
    const fetchMock = mockApi({
      '/api/auth/refresh': () => session(),
      '/api/timeline/home': () => page(posted ? [post('p1', 'こんにちは')] : []),
      '/api/posts': () => {
        posted = true
        return json(201, post('p1', 'こんにちは'))
      },
    })
    renderApp('/home')

    await userEvent.click((await screen.findAllByRole('button', { name: '投稿する' }))[0])
    const dialog = screen.getByRole('dialog', { name: '投稿する' })
    const submit = within(dialog).getByRole('button', { name: '投稿する' })
    expect(submit).toBeDisabled()

    await userEvent.type(within(dialog).getByLabelText('本文'), 'こんにちは')
    expect(within(dialog).getByLabelText('残り文字数')).toHaveTextContent('275')
    await userEvent.click(submit)

    expect(await screen.findByText('投稿しました')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByText('こんにちは')).toBeInTheDocument()
    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/posts') as unknown as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect((init.body as FormData).get('body')).toBe('こんにちは')
  })

  it('280 文字を超えると投稿できない（絵文字は 1 文字と数える）', async () => {
    mockApi({ '/api/auth/refresh': () => session() })
    renderApp('/home')

    await userEvent.click((await screen.findAllByRole('button', { name: '投稿する' }))[0])
    const dialog = screen.getByRole('dialog', { name: '投稿する' })
    await userEvent.click(within(dialog).getByLabelText('本文'))
    await userEvent.paste('😀'.repeat(280))
    expect(within(dialog).getByRole('button', { name: '投稿する' })).toBeEnabled()

    await userEvent.paste('a')
    expect(within(dialog).getByLabelText('残り文字数')).toHaveTextContent('-1')
    expect(within(dialog).getByRole('button', { name: '投稿する' })).toBeDisabled()
  })

  it('サーバーの入力エラーを表示する', async () => {
    mockApi({
      '/api/auth/refresh': () => session(),
      '/api/posts': () =>
        json(400, { code: 'VALIDATION_FAILED', detail: '入力内容に誤りがあります', errors: { body: '本文を入力してください' } }),
    })
    renderApp('/home')

    await userEvent.click((await screen.findAllByRole('button', { name: '投稿する' }))[0])
    const dialog = screen.getByRole('dialog', { name: '投稿する' })
    await userEvent.type(within(dialog).getByLabelText('本文'), 'x')
    await userEvent.click(within(dialog).getByRole('button', { name: '投稿する' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('本文を入力してください')
  })

  it('自分の投稿だけ削除でき、削除するとタイムラインから消える', async () => {
    vi.stubGlobal('confirm', () => true)
    let deleted = false
    const fetchMock = mockApi({
      '/api/auth/refresh': () => session(),
      '/api/timeline/home': () =>
        page(deleted ? [post('p1', '花子の投稿', hanako)] : [post('p2', '自分の投稿'), post('p1', '花子の投稿', hanako)]),
      '/api/posts/p2': () => {
        deleted = true
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/home')

    const [mine, others] = await screen.findAllByRole('article')
    expect(within(others).queryByRole('button', { name: '投稿のメニュー' })).not.toBeInTheDocument()
    await userEvent.click(within(mine).getByRole('button', { name: '投稿のメニュー' }))
    await userEvent.click(within(mine).getByRole('button', { name: '削除' }))

    expect(await screen.findByText('投稿を削除しました')).toBeInTheDocument()
    expect(await screen.findAllByRole('article')).toHaveLength(1)
    expect(screen.queryByText('自分の投稿')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/posts/p2', expect.objectContaining({ method: 'DELETE' }))
  })
})
