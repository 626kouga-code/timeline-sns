import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { deferred, json, mockApi, noSession, session, user } from '../test/utils'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './context'

const authResponse = { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user }

function Who() {
  const { me, login, logout, setMe } = useAuth()
  return (
    <div>
      <p>{me ? `@${me.handle}` : 'ゲスト'}</p>
      <button type="button" onClick={() => void login({ email: 'taro@example.com', password: 'password123' })}>
        ログイン
      </button>
      <button type="button" onClick={() => void logout().catch(() => {})}>
        ログアウト
      </button>
      <button type="button" onClick={() => setMe({ ...user, displayName: '改名' })}>
        改名
      </button>
      <p>{me?.displayName}</p>
    </div>
  )
}

function setup() {
  const queryClient = new QueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Who />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return { queryClient }
}

describe('AuthProvider', () => {
  it('起動時のログイン状態の復元が終わるまで、画面を出さずに読み込み中を出す', async () => {
    const refresh = deferred<Response>()
    mockApi({ '/api/auth/refresh': () => refresh.promise })
    setup()

    expect(screen.getByRole('status', { name: '読み込み中' })).toBeInTheDocument()
    expect(screen.queryByText('ゲスト')).not.toBeInTheDocument()

    refresh.resolve(session())
    expect(await screen.findByText('@taro')).toBeInTheDocument()
  })

  it('復元できなければゲストとして始める', async () => {
    mockApi({ '/api/auth/refresh': noSession })
    setup()

    expect(await screen.findByText('ゲスト')).toBeInTheDocument()
  })

  it('ログインするとユーザーが入る', async () => {
    mockApi({ '/api/auth/login': () => json(200, authResponse) })
    setup()

    await userEvent.click(await screen.findByRole('button', { name: 'ログイン' }))

    expect(await screen.findByText('@taro')).toBeInTheDocument()
  })

  it('ログアウトするとユーザーを消し、前のユーザーのキャッシュも消す', async () => {
    mockApi({ '/api/auth/refresh': session, '/api/auth/logout': () => new Response(null, { status: 204 }) })
    const { queryClient } = setup()
    await screen.findByText('@taro')
    queryClient.setQueryData(['notifications', user.id, 'list'], { items: [] })

    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(await screen.findByText('ゲスト')).toBeInTheDocument()
    expect(queryClient.getQueryData(['notifications', user.id, 'list'])).toBeUndefined()
  })

  it('サーバーへのログアウトに失敗しても、手元ではログアウトする', async () => {
    mockApi({ '/api/auth/refresh': session, '/api/auth/logout': () => json(500, { code: 'INTERNAL', detail: 'エラー' }) })
    setup()
    await screen.findByText('@taro')

    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(await screen.findByText('ゲスト')).toBeInTheDocument()
  })

  it('setMe で本人の情報を差し替えられる', async () => {
    mockApi({ '/api/auth/refresh': session })
    setup()
    await screen.findByText('@taro')

    await userEvent.click(screen.getByRole('button', { name: '改名' }))

    expect(screen.getByText('改名', { selector: 'p' })).toBeInTheDocument()
  })

  it('AuthProvider の外で useAuth を使うとエラーにする', () => {
    // React が投げられたエラーをコンソールにも出すので、テストの出力を汚さないよう止める
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => render(<Who />)).toThrow('useAuth は AuthProvider の内側で使ってください')
    consoleError.mockRestore()
  })
})
