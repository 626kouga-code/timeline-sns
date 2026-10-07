import { apiFetch } from './client'
import type { Notification, Page } from './types'

/** TanStack Query のキー。本人の通知なので、キーに本人の ID を含める */
export const notificationKeys = {
  all: ['notifications'] as const,
  list: (me: string) => ['notifications', me, 'list'] as const,
  unreadCount: (me: string) => ['notifications', me, 'unread-count'] as const,
}

/** 通知一覧（F-50）。新しい順。cursor には前のページの nextCursor を渡す */
export function fetchNotifications(cursor: string | null): Promise<Page<Notification>> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  return apiFetch<Page<Notification>>(`/api/notifications${query}`)
}

/** 未読の数（F-51）。100 件で打ち切る */
export function fetchUnreadCount(): Promise<{ count: number }> {
  return apiFetch<{ count: number }>('/api/notifications/unread-count')
}

/** 既読にする（F-51）。until（画面に表示した一番新しい通知）以前だけを既読にする */
export function markNotificationsRead(until: string): Promise<void> {
  return apiFetch<void>('/api/notifications/read', { method: 'POST', body: { until } })
}
