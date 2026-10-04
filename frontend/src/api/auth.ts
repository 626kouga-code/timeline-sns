import { apiFetch, refreshSession, setAccessToken } from './client'
import type { AuthResponse, Me } from './types'

export interface SignupInput {
  email: string
  password: string
  handle: string
  displayName: string
  agreedToTerms: boolean
}

export interface LoginInput {
  email: string
  password: string
}

async function startSession(promise: Promise<AuthResponse>): Promise<Me> {
  const auth = await promise
  setAccessToken(auth.accessToken)
  return auth.user
}

/** 新規登録（F-01）。成功するとそのままログイン状態になる */
export function signup(input: SignupInput): Promise<Me> {
  return startSession(apiFetch<AuthResponse>('/api/auth/signup', { method: 'POST', body: input }))
}

/** ログイン（F-03） */
export function login(input: LoginInput): Promise<Me> {
  return startSession(apiFetch<AuthResponse>('/api/auth/login', { method: 'POST', body: input }))
}

/** リロード後などに Cookie からログイン状態を復元する */
export async function restoreSession(): Promise<Me> {
  return (await refreshSession()).user
}

/** ログアウト。サーバー側の失敗に関係なく、手元のトークンは必ず捨てる */
export async function logout(): Promise<void> {
  try {
    await apiFetch<void>('/api/auth/logout', { method: 'POST' })
  } finally {
    setAccessToken(null)
  }
}
