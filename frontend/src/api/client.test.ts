import { describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, refreshSession, setAccessToken } from './client'
import type { AuthResponse } from './types'

const user = {
  id: 'u1',
  email: 'taro@example.com',
  handle: 'taro',
  displayName: '太郎',
  bio: null,
  role: 'USER',
  emailVerified: false,
} as const

const authResponse = (accessToken: string): AuthResponse => ({ accessToken, tokenType: 'Bearer', expiresIn: 900, user })

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const authHeader = (init: RequestInit | undefined) => new Headers(init?.headers).get('Authorization')

describe('apiFetch', () => {
  it('アクセストークンを Bearer で付けて JSON を返す', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('token-1')

    await expect(apiFetch('/api/me')).resolves.toEqual({ ok: true })
    expect(authHeader(fetchMock.mock.calls[0][1])).toBe('Bearer token-1')
  })

  it('401 ならリフレッシュして新しいトークンで 1 回だけ再送する', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED', detail: 'ログインしてください' }))
      .mockResolvedValueOnce(json(200, authResponse('token-2')))
      .mockResolvedValueOnce(json(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('expired')

    await expect(apiFetch('/api/me')).resolves.toEqual({ ok: true })
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(['/api/me', '/api/auth/refresh', '/api/me'])
    expect(authHeader(fetchMock.mock.calls[2][1])).toBe('Bearer token-2')
  })

  it('リフレッシュにも失敗したら元の 401 をエラーにする', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED', detail: 'ログインしてください' }))
      .mockResolvedValueOnce(json(401, { code: 'INVALID_REFRESH_TOKEN', detail: '期限切れ' }))
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('expired')

    await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('ProblemDetail を ApiError に変換する', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        json(409, {
          code: 'EMAIL_TAKEN',
          detail: 'このメールアドレスは既に登録されています',
          errors: { email: 'このメールアドレスは既に登録されています' },
        }),
      ),
    )

    const error = await apiFetch('/api/auth/signup', { method: 'POST', body: {} }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 409,
      code: 'EMAIL_TAKEN',
      message: 'このメールアドレスは既に登録されています',
      errors: { email: 'このメールアドレスは既に登録されています' },
    })
  })

  it('サーバーに接続できないときは NETWORK_ERROR にする', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })
})

describe('refreshSession', () => {
  it('同時に呼ばれてもリフレッシュは 1 回しか送らない', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, authResponse('token-3')))
    vi.stubGlobal('fetch', fetchMock)

    const [a, b] = await Promise.all([refreshSession(), refreshSession()])
    expect(a).toBe(b)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
