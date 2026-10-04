import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import App from '../App'
import { AppProviders } from '../AppProviders'
import type { Me, Post } from '../api/types'

export const user: Me = {
  id: 'u1',
  email: 'taro@example.com',
  handle: 'taro',
  displayName: '太郎',
  bio: null,
  role: 'USER',
  emailVerified: false,
}

export const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

export const noSession = () => json(401, { code: 'INVALID_REFRESH_TOKEN', detail: 'ログインしてください' })

/** 起動時のリフレッシュでログイン状態にする応答 */
export const session = () => json(200, { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user })

export const page = (items: Post[], nextCursor: string | null = null) => json(200, { items, nextCursor })

export function post(id: string, body: string, author: Pick<Me, 'id' | 'handle' | 'displayName'> = user): Post {
  return {
    id,
    body,
    createdAt: new Date().toISOString(),
    author: { id: author.id, handle: author.handle, displayName: author.displayName },
    likeCount: 0,
    commentCount: 0,
    liked: false,
  }
}

type Handler = (path: string, init?: RequestInit) => Response | Promise<Response>

// 指定がなければタイムラインは空にする
const defaults: Record<string, Handler> = {
  '/api/timeline/global': () => page([]),
  '/api/timeline/home': () => page([]),
}

/**
 * パスごとに応答を返す fetch のモック。クエリ文字列つきのパスで見つからなければ、クエリを除いたパスで探す。
 * 指定のないパス（起動時のリフレッシュなど）は未ログイン（401）にする。
 */
export function mockApi(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
    const pathname = path.split('?')[0]
    const handler = handlers[path] ?? handlers[pathname] ?? defaults[pathname] ?? noSession
    return handler(path, init)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export function renderApp(path: string, state?: unknown) {
  render(
    <MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <AppProviders>
        <App />
      </AppProviders>
    </MemoryRouter>,
  )
}
