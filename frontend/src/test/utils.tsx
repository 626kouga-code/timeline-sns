import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { vi } from 'vitest'
import App from '../App'
import { AppProviders } from '../AppProviders'
import type { Me, Post } from '../api/types'
import { AuthContext, type AuthContextValue } from '../auth/context'
import { ToastProvider } from '../components/ToastProvider'
import { LocationProbe } from './LocationProbe'

export const user: Me = {
  id: 'u1',
  email: 'taro@example.com',
  handle: 'taro',
  displayName: '太郎',
  bio: null,
  avatarUrl: null,
  role: 'USER',
  emailVerified: false,
}

export const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

export const noSession = () => json(401, { code: 'INVALID_REFRESH_TOKEN', detail: 'ログインしてください' })

/** 起動時のリフレッシュでログイン状態にする応答 */
export const session = () => json(200, { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user })

export const page = <T,>(items: T[], nextCursor: string | null = null) => json(200, { items, nextCursor })

export function post(
  id: string,
  body: string,
  author: Pick<Me, 'id' | 'handle' | 'displayName'> & { avatarUrl?: string | null } = user,
): Post {
  return {
    id,
    body,
    images: [],
    createdAt: new Date().toISOString(),
    author: { id: author.id, handle: author.handle, displayName: author.displayName, avatarUrl: author.avatarUrl ?? null },
    likeCount: 0,
    commentCount: 0,
    liked: false,
  }
}

type Handler = (path: string, init?: RequestInit) => Response | Promise<Response>

// 指定がなければタイムラインとコメント欄は空、未読の通知は 0 件にする
const defaults: Record<string, Handler> = {
  '/api/timeline/global': () => page([]),
  '/api/timeline/home': () => page([]),
  '/api/notifications/unread-count': () => json(200, { count: 0 }),
}

/**
 * パスごとに応答を返す fetch のモック。クエリ文字列つきのパスで見つからなければ、クエリを除いたパスで探す。
 * 指定のないパス（起動時のリフレッシュなど）は未ログイン（401）にする。コメント一覧だけは空の配列を返す。
 */
export function mockApi(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
    const pathname = path.split('?')[0]
    const fallback = /^\/api\/posts\/[^/]+\/comments$/.test(pathname) ? () => json(200, []) : noSession
    const handler = handlers[path] ?? handlers[pathname] ?? defaults[pathname] ?? fallback
    return handler(path, init)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** path にはクエリ文字列（`/search?q=…`）も含められる */
export function renderApp(path: string, state?: unknown) {
  const [pathname, query] = path.split('?')
  render(
    <MemoryRouter initialEntries={[{ pathname, search: query ? `?${query}` : '', state }]}>
      <AppProviders>
        <App />
      </AppProviders>
    </MemoryRouter>,
  )
}

interface RenderOptions {
  /** ログイン中のユーザー。null（既定）なら未ログイン */
  me?: Me | null
  /** 最初に表示しているパス。履歴を複数積むときは配列で渡す（最後が今の画面） */
  path?: string | string[]
  /** コンポーネントを置くルートのパス。useParams を使う部品のテストで指定する */
  route?: string
}

/**
 * コンポーネント単体を、アプリと同じ Provider（TanStack Query・トースト・ログイン状態・ルーター）で包んで描画する。
 * ログイン状態は起動時のリフレッシュをせずに `me` で決める。`auth` の login / logout などは vi.fn() にしてある。
 * 画面遷移は `screen.getByTestId('location')` の中身（パス）と data-from（ログイン後に戻る先）で確かめる。
 */
export function renderWithProviders(ui: ReactElement, { me = null, path = '/', route = '*' }: RenderOptions = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } },
  })
  const auth: AuthContextValue = {
    me,
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(() => Promise.resolve()),
    setMe: vi.fn(),
  }
  const entries = Array.isArray(path) ? path : [path]
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthContext.Provider value={auth}>
          <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
            <Routes>
              <Route path={route} element={ui} />
            </Routes>
            <LocationProbe />
          </MemoryRouter>
        </AuthContext.Provider>
      </ToastProvider>
    </QueryClientProvider>,
  )
  return { ...result, queryClient, auth }
}

/** 解決を外から決められる Promise（送信中の表示を確かめるときに、応答を待たせる） */
export function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** fetch のモックに渡された n 回目のリクエスト（パス・メソッド・ヘッダー・本文） */
export function request(fetchMock: ReturnType<typeof mockApi>, n = 0) {
  const [path, init] = fetchMock.mock.calls[n]
  return {
    path,
    method: init?.method ?? 'GET',
    headers: new Headers(init?.headers),
    body: init?.body,
  }
}
