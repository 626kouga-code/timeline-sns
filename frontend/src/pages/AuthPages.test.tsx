import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { json, mockApi, renderApp, session, user } from '../test/utils'

describe('ログイン画面', () => {
  it('ログインに成功したら、開こうとしていた画面へ移る', async () => {
    const fetchMock = mockApi({
      '/api/auth/login': () => session(),
    })
    renderApp('/login', { from: '/notifications' })

    await userEvent.type(await screen.findByLabelText('メールアドレス'), 'taro@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByRole('heading', { name: '通知' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'ログアウト' }).length).toBeGreaterThan(0)
    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/auth/login') as unknown as [string, RequestInit]
    expect(JSON.parse(init.body as string)).toEqual({ email: 'taro@example.com', password: 'password123' })
  })

  it('認証に失敗したらサーバーのメッセージを表示する', async () => {
    mockApi({
      '/api/auth/login': () =>
        json(401, { code: 'INVALID_CREDENTIALS', detail: 'メールアドレスまたはパスワードが正しくありません' }),
    })
    renderApp('/login')

    await userEvent.type(await screen.findByLabelText('メールアドレス'), 'taro@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('メールアドレスまたはパスワードが正しくありません')
  })

  it('ログイン必須の画面でログアウトすると、ログイン画面ではなくトップへ移る', async () => {
    mockApi({
      '/api/auth/refresh': () => session(),
      '/api/auth/logout': () => new Response(null, { status: 204 }),
    })
    renderApp('/home')

    await screen.findByRole('heading', { name: 'ホーム' })
    await userEvent.click(screen.getAllByRole('button', { name: 'ログアウト' })[0])

    expect(await screen.findByRole('heading', { name: '全体タイムライン' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'ログアウト' })).not.toBeInTheDocument()
  })

  it('ログイン必須の画面を開くとログイン画面へ移る', async () => {
    mockApi({})
    renderApp('/home')

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })
})

describe('新規登録画面', () => {
  it('入力に誤りがあると送信せずにエラーを表示する', async () => {
    const fetchMock = mockApi({})
    renderApp('/signup')

    await userEvent.type(await screen.findByLabelText('ユーザーID'), 'ab')
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(screen.getByText('ユーザーIDは英数字と _ で 3〜15 文字にしてください')).toBeInTheDocument()
    expect(screen.getByText('利用規約とプライバシーポリシーへの同意が必要です')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/signup', expect.anything())
  })

  it('サーバーの項目別エラー（重複など）を該当の項目に表示する', async () => {
    mockApi({
      '/api/auth/signup': () =>
        json(409, {
          code: 'HANDLE_TAKEN',
          detail: 'このユーザーIDは既に使われています',
          errors: { handle: 'このユーザーIDは既に使われています' },
        }),
    })
    renderApp('/signup')

    await userEvent.type(await screen.findByLabelText('メールアドレス'), 'taro@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'password123')
    await userEvent.type(screen.getByLabelText('ユーザーID'), 'taro')
    await userEvent.type(screen.getByLabelText('表示名'), '太郎')
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText('このユーザーIDは既に使われています')).toBeInTheDocument()
    expect(screen.getByLabelText('ユーザーID')).toHaveAttribute('aria-invalid', 'true')
  })

  it('登録に成功したらホームへ移る', async () => {
    mockApi({
      '/api/auth/signup': () => json(201, { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user }),
    })
    renderApp('/signup')

    await userEvent.type(await screen.findByLabelText('メールアドレス'), 'taro@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'password123')
    await userEvent.type(screen.getByLabelText('ユーザーID'), 'taro')
    await userEvent.type(screen.getByLabelText('表示名'), '太郎')
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })
})
