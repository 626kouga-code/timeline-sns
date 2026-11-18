// テストの前に主な API を何度か呼んで、起動したばかりのバックエンドを温める。
// 起動直後の JVM は JIT（実行時の最適化）が効いておらず、最初の数回の応答が数倍遅い。
// 本番のサーバーは起動しっぱなしなので、この遅さを表示時間（2 秒の判定）に混ぜないようにする。
const ROUNDS = 30

export async function warmUp(baseURL: string): Promise<void> {
  const api = (path: string, init: RequestInit = {}) => fetch(`${baseURL}/api${path}`, init)
  const handle = `warm${Date.now().toString(36).slice(-8)}`
  const password = 'warmup-password'
  const json = { 'Content-Type': 'application/json' }

  const signup = await api('/auth/signup', {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ email: `${handle}@example.com`, password, handle, displayName: 'warm up', agreedToTerms: true }),
  })
  if (!signup.ok) throw new Error(`ウォームアップのユーザー登録に失敗しました (${signup.status})`)
  const { accessToken } = (await signup.json()) as { accessToken: string }
  const auth = { Authorization: `Bearer ${accessToken}` }
  let cookie = signup.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')

  const form = new FormData()
  form.set('body', 'ウォームアップの投稿')
  const post = (await (await api('/posts', { method: 'POST', headers: auth, body: form })).json()) as { id: string }
  await api(`/posts/${post.id}/comments`, { method: 'POST', headers: { ...auth, ...json }, body: '{"body":"warm up"}' })

  for (let i = 0; i < ROUNDS; i++) {
    // ログイン状態の復元（refresh）は Cookie を毎回差し替える（トークンがローテーションされるため）
    const refresh = await api('/auth/refresh', { method: 'POST', headers: { Cookie: cookie } })
    cookie = refresh.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ') || cookie
    await Promise.all([
      api('/timeline/global'),
      api('/timeline/home', { headers: auth }),
      api(`/timeline/home/new-count?since=${post.id}`, { headers: auth }),
      api('/me', { headers: auth }),
      api(`/users/${handle}`, { headers: auth }),
      api(`/users/${handle}/posts`, { headers: auth }),
      api(`/posts/${post.id}`, { headers: auth }),
      api(`/posts/${post.id}/comments`, { headers: auth }),
      api('/notifications/unread-count', { headers: auth }),
      api(`/search/users?q=${handle.slice(0, 6)}`),
    ])
  }
}
