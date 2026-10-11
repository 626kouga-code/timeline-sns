import { describe, expect, it } from 'vitest'
import { json, mockApi, page, request } from '../test/utils'
import { fetchNotifications, fetchUnreadCount, markNotificationsRead, notificationKeys } from './notifications'
import type { Notification } from './types'

const notification: Notification = {
  id: 'n1',
  type: 'LIKE',
  createdAt: '2026-10-05T00:00:00Z',
  unread: true,
  actor: { id: 'u2', handle: 'hanako', displayName: '花子', avatarUrl: null },
  post: { id: 'p1', body: '投稿', thumbnailUrl: null },
  comment: null,
}

describe('通知の API', () => {
  it('一覧は cursor なし・ありで取る', async () => {
    const fetchMock = mockApi({
      '/api/notifications': () => page([notification], 'n1'),
      '/api/notifications?cursor=n1': () => page([]),
    })

    await expect(fetchNotifications(null)).resolves.toEqual({ items: [notification], nextCursor: 'n1' })
    await fetchNotifications('n1')

    expect(request(fetchMock, 1).path).toBe('/api/notifications?cursor=n1')
  })

  it('未読数を取る', async () => {
    mockApi({ '/api/notifications/unread-count': () => json(200, { count: 7 }) })

    await expect(fetchUnreadCount()).resolves.toEqual({ count: 7 })
  })

  it('既読化は表示した一番新しい通知（until）を送る', async () => {
    const fetchMock = mockApi({ '/api/notifications/read': () => new Response(null, { status: 204 }) })

    await expect(markNotificationsRead('n5')).resolves.toBeUndefined()

    expect(request(fetchMock).method).toBe('POST')
    expect(JSON.parse(request(fetchMock).body as string)).toEqual({ until: 'n5' })
  })

  it('キャッシュのキーは本人ごとに分かれる', () => {
    expect(notificationKeys.list('u1')).not.toEqual(notificationKeys.list('u2'))
    expect(notificationKeys.unreadCount('u1')[0]).toBe(notificationKeys.all[0])
  })
})
