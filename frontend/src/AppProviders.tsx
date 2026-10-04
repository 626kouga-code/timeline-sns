import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { ApiError } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { ToastProvider } from './components/ToastProvider'

// 4xx（存在しない・権限がない・入力が不正）は取り直しても結果が変わらないので、通信エラーや 5xx だけ 1 回再試行する
function shouldRetry(failureCount: number, error: Error) {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < 1
}

// ルーター以外の Provider をまとめる（テストでは BrowserRouter の代わりに MemoryRouter で包む）
export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: shouldRetry, refetchOnWindowFocus: false } },
      }),
  )
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthProvider>{children}</AuthProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
}
