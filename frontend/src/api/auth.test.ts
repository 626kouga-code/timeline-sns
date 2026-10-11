import { describe, expect, it } from 'vitest'
import { json, mockApi, request, user } from '../test/utils'
import { login, logout, restoreSession, signup } from './auth'
import { apiFetch } from './client'

const authResponse = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900, user })

describe('signup', () => {
  it('入力を JSON で送り、受け取ったトークンで以後の API を呼ぶ', async () => {
    const fetchMock = mockApi({
      '/api/auth/signup': () => json(201, authResponse('token-signup')),
      '/api/me': () => json(200, user),
    })
    const input = {
      email: 'taro@example.com',
      password: 'password123',
      handle: 'taro',
      displayName: '太郎',
      agreedToTerms: true,
    }

    await expect(signup(input)).resolves.toEqual(user)

    const sent = request(fetchMock, 0)
    expect(sent.method).toBe('POST')
    expect(sent.headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(sent.body as string)).toEqual(input)
    await apiFetch('/api/me')
    expect(request(fetchMock, 1).headers.get('Authorization')).toBe('Bearer token-signup')
  })

  it('入力エラーは項目ごとのメッセージ付きの ApiError になり、ログイン状態にならない', async () => {
    const fetchMock = mockApi({
      '/api/auth/signup': () =>
        json(400, { code: 'VALIDATION_FAILED', detail: '入力内容に誤りがあります', errors: { handle: '使えません' } }),
      '/api/me': () => json(200, user),
    })

    await expect(
      signup({ email: 'a@example.com', password: 'p', handle: '!', displayName: 'a', agreedToTerms: true }),
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_FAILED', errors: { handle: '使えません' } })
    await apiFetch('/api/me')
    expect(request(fetchMock, 1).headers.get('Authorization')).toBeNull()
  })
})

describe('login', () => {
  it('メールとパスワードを送ってユーザーを返す', async () => {
    const fetchMock = mockApi({ '/api/auth/login': () => json(200, authResponse('token-login')) })

    await expect(login({ email: 'taro@example.com', password: 'password123' })).resolves.toEqual(user)

    expect(request(fetchMock).path).toBe('/api/auth/login')
    expect(JSON.parse(request(fetchMock).body as string)).toEqual({
      email: 'taro@example.com',
      password: 'password123',
    })
  })

  it('パスワード違い（401）はリフレッシュせずにそのままエラーにする', async () => {
    const fetchMock = mockApi({
      '/api/auth/login': () =>
        json(401, { code: 'INVALID_CREDENTIALS', detail: 'メールアドレスまたはパスワードが正しくありません' }),
    })

    await expect(login({ email: 'taro@example.com', password: 'wrong' })).rejects.toMatchObject({
      status: 401,
      code: 'INVALID_CREDENTIALS',
      message: 'メールアドレスまたはパスワードが正しくありません',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('restoreSession', () => {
  it('Cookie のリフレッシュトークンでユーザーを復元する', async () => {
    const fetchMock = mockApi({ '/api/auth/refresh': () => json(200, authResponse('token-restored')) })

    await expect(restoreSession()).resolves.toEqual(user)
    expect(request(fetchMock).method).toBe('POST')
  })

  it('未ログインなら 401 のエラーになる', async () => {
    mockApi({})

    await expect(restoreSession()).rejects.toMatchObject({ status: 401 })
  })
})

describe('logout', () => {
  it('サーバーに知らせ、手元のトークンを捨てる', async () => {
    const fetchMock = mockApi({
      '/api/auth/login': () => json(200, authResponse('token-login')),
      '/api/auth/logout': () => new Response(null, { status: 204 }),
      '/api/me': () => json(200, user),
    })
    await login({ email: 'taro@example.com', password: 'password123' })

    await logout()

    expect(request(fetchMock, 1)).toMatchObject({ path: '/api/auth/logout', method: 'POST' })
    await apiFetch('/api/me')
    expect(request(fetchMock, 2).headers.get('Authorization')).toBeNull()
  })

  it('サーバーへの通知に失敗しても、手元のトークンは捨てる', async () => {
    const fetchMock = mockApi({
      '/api/auth/login': () => json(200, authResponse('token-login')),
      '/api/auth/logout': () => json(500, { code: 'INTERNAL', detail: 'エラー' }),
      '/api/me': () => json(200, user),
    })
    await login({ email: 'taro@example.com', password: 'password123' })

    await expect(logout()).rejects.toMatchObject({ status: 500 })

    await apiFetch('/api/me')
    expect(request(fetchMock, 2).headers.get('Authorization')).toBeNull()
  })
})
