import type { AuthResponse, Me } from './types'

/**
 * API のエラー。バックエンドの ProblemDetail（application/problem+json）を変換したもの。
 * `code` は分岐用の識別子、`message` は画面にそのまま出せる日本語、`errors` は入力項目ごとのメッセージ。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: Record<string, string>

  constructor(status: number, code: string, message: string, errors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.errors = errors
  }
}

interface Problem {
  detail?: string
  code?: string
  errors?: Record<string, string>
}

// アクセストークンは XSS で盗まれにくいようメモリにだけ置く（リロードで消えたらリフレッシュで取り直す）
let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

type SessionListener = (user: Me | null) => void
const sessionListeners = new Set<SessionListener>()

/** リフレッシュの成功・失敗でログイン状態が変わったときに呼ばれる */
export function onSessionChange(listener: SessionListener) {
  sessionListeners.add(listener)
  return () => {
    sessionListeners.delete(listener)
  }
}

async function toApiError(res: Response): Promise<ApiError> {
  let problem: Problem = {}
  try {
    problem = (await res.json()) as Problem
  } catch {
    // 本文が JSON でない（プロキシのエラーなど）
  }
  return new ApiError(
    res.status,
    problem.code ?? 'UNKNOWN',
    problem.detail ?? '通信に失敗しました。時間をおいてもう一度お試しください',
    problem.errors ?? {},
  )
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  if (init.body !== undefined && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  try {
    return await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'サーバーに接続できませんでした。通信環境を確認してください')
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

let refreshing: Promise<AuthResponse> | null = null

/**
 * リフレッシュトークン（Cookie）で新しいアクセストークンを取得する。
 *
 * バックエンドはリフレッシュトークンを使うたびに入れ替え、失効済みのトークンが再び使われると
 * 盗用とみなして全セッションを失効させる。そのため同時に 2 回送ってはいけない。
 * タブ内は実行中の Promise を共有し、タブ間は Web Locks で順番に実行する。
 */
export function refreshSession(): Promise<AuthResponse> {
  refreshing ??= withLock(async () => {
    const res = await send('/api/auth/refresh', { method: 'POST' })
    return parse<AuthResponse>(res)
  })
    .then((auth) => {
      setAccessToken(auth.accessToken)
      sessionListeners.forEach((listener) => listener(auth.user))
      return auth
    })
    .catch((error: unknown) => {
      setAccessToken(null)
      sessionListeners.forEach((listener) => listener(null))
      throw error
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

function withLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request('timeline-auth-refresh', task)
  }
  return task()
}

/**
 * API を呼び出す。ログイン中ならアクセストークンを付け、期限切れ（401）ならリフレッシュして 1 回だけ再送する。
 * `body` にオブジェクトを渡すと JSON にして送る。
 */
export async function apiFetch<T>(
  path: string,
  { body, ...init }: Omit<RequestInit, 'body'> & { body?: unknown } = {},
): Promise<T> {
  const requestInit: RequestInit = {
    ...init,
    body: body === undefined || body instanceof FormData ? body : JSON.stringify(body),
  }
  const hadToken = accessToken !== null
  const res = await send(path, requestInit)
  // 認証 API 自体の 401（パスワード違いなど）はリフレッシュの対象外
  if (res.status === 401 && hadToken && !path.startsWith('/api/auth/')) {
    try {
      await refreshSession()
    } catch {
      throw await toApiError(res)
    }
    return parse<T>(await send(path, requestInit))
  }
  return parse<T>(res)
}
