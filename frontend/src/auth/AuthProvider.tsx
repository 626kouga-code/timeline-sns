import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { onSessionChange } from '../api/client'
import type { Me } from '../api/types'
import { AuthContext, type AuthContextValue } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [me, setMe] = useState<Me | null>(null)
  const [restoring, setRestoring] = useState(true)

  useEffect(() => {
    const unsubscribe = onSessionChange(setMe)
    // リロード直後はアクセストークンがないので、Cookie のリフレッシュトークンで復元を試みる
    authApi
      .restoreSession()
      .catch(() => null)
      .finally(() => setRestoring(false))
    return unsubscribe
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      me,
      login: async (input) => {
        const user = await authApi.login(input)
        setMe(user)
        return user
      },
      signup: async (input) => {
        const user = await authApi.signup(input)
        setMe(user)
        return user
      },
      logout: async () => {
        try {
          await authApi.logout()
        } finally {
          setMe(null)
          // 前のユーザーのデータを次のユーザーに見せない
          queryClient.clear()
        }
      },
    }),
    [me, queryClient],
  )

  // 復元が終わるまでは画面を出さない（ログイン済みなのに一瞬 /login へ飛ばされるのを防ぐ）
  if (restoring) {
    return (
      <div className="flex min-h-screen items-center justify-center" role="status" aria-label="読み込み中">
        <span className="size-8 animate-spin rounded-full border-4 border-sky-100 border-t-sky-500" />
      </div>
    )
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
