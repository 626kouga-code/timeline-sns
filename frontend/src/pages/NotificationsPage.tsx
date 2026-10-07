import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, useNavigate } from 'react-router'
import { fetchNotifications, markNotificationsRead, notificationKeys } from '../api/notifications'
import type { Notification, NotificationType } from '../api/types'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { Icon, type IconName } from '../components/Icon'
import { LoadMore } from '../components/LoadMore'
import { PageHeader } from '../components/PageHeader'
import { formatRelative } from '../components/time'

const TYPE_INFO: Record<NotificationType, { icon: IconName; color: string; text: string }> = {
  LIKE: { icon: 'heart', color: 'text-pink-600', text: 'があなたの投稿にいいねしました' },
  COMMENT: { icon: 'comment', color: 'text-sky-600', text: 'があなたの投稿にコメントしました' },
  REPLY: { icon: 'comment', color: 'text-emerald-600', text: 'があなたのコメントに返信しました' },
  FOLLOW: { icon: 'user', color: 'text-violet-600', text: 'にフォローされました' },
}

// 通知一覧（F-50, `/notifications`）。開いたら、表示した分までを既読にする（F-51）
export function NotificationsPage() {
  const { me } = useAuth()
  const queryClient = useQueryClient()
  const viewer = me?.id ?? ''

  const list = useInfiniteQuery({
    queryKey: notificationKeys.list(viewer),
    queryFn: ({ pageParam }) => fetchNotifications(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    // 開くたびに取り直す。開いている間は取り直さず、未読だった通知の強調表示を残す
    staleTime: Infinity,
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
    enabled: !!me,
  })
  const notifications = list.data?.pages.flatMap((page) => page.items) ?? []
  const newestId = notifications[0]?.id

  const markRead = useMutation({
    mutationFn: markNotificationsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount(viewer) }),
  })
  const { mutate } = markRead
  const hasUnread = notifications.some((n) => n.unread)

  // 一覧を表示したら、その一番新しい通知までを既読にする（開いている間に届いた通知は未読のまま残す）
  useEffect(() => {
    if (newestId && hasUnread) mutate(newestId)
  }, [newestId, hasUnread, mutate])

  if (!me) return null

  return (
    <>
      <PageHeader title="通知" />
      {list.isPending && <p className="py-10 text-center text-sm text-slate-400">読み込み中…</p>}
      {list.isError && notifications.length === 0 && (
        <div className="px-8 py-16 text-center">
          <p role="alert" className="text-slate-600">
            {list.error.message}
          </p>
          <button
            type="button"
            onClick={() => void list.refetch()}
            className="mt-4 rounded-full border border-slate-300 px-4 py-2 text-sm font-bold hover:bg-slate-50"
          >
            再読み込み
          </button>
        </div>
      )}
      {list.isSuccess && notifications.length === 0 && (
        <p className="px-4 py-16 text-center text-slate-500">まだ通知はありません</p>
      )}
      <ul>
        {notifications.map((n) => (
          <NotificationItem key={n.id} notification={n} />
        ))}
      </ul>
      <LoadMore
        hasNextPage={list.hasNextPage}
        isFetchingNextPage={list.isFetchingNextPage}
        fetchNextPage={list.fetchNextPage}
        isEmpty={notifications.length === 0}
      />
    </>
  )
}

function NotificationItem({ notification: n }: { notification: Notification }) {
  const navigate = useNavigate()
  const info = TYPE_INFO[n.type]
  // 投稿への反応は投稿へ、フォローは相手のプロフィールへ
  const to = n.post ? `/posts/${n.post.id}` : `/users/${n.actor.handle}`

  return (
    <li
      onClick={() => navigate(to)}
      className={`flex cursor-pointer gap-3 border-b border-slate-100 px-4 py-3 hover:bg-slate-50 ${n.unread ? 'bg-sky-50/60' : ''}`}
    >
      <Icon name={info.icon} filled={n.type === 'LIKE'} className={`mt-1 size-6 shrink-0 ${info.color}`} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between">
          <Link to={`/users/${n.actor.handle}`} onClick={(e) => e.stopPropagation()}>
            <Avatar user={n.actor} size="sm" />
          </Link>
          <span className="flex items-center gap-2 text-xs text-slate-500">
            {n.unread && <span className="size-2 rounded-full bg-sky-500" role="img" aria-label="未読" />}
            <time dateTime={n.createdAt}>{formatRelative(n.createdAt)}</time>
          </span>
        </div>
        <p className="mt-1.5 text-sm break-words">
          <b>{n.actor.displayName}</b>
          <span className="text-slate-500"> さん</span>
          {info.text}
        </p>
        {n.comment && (
          <p className={`mt-1 text-sm break-words ${n.comment.body === null ? 'text-slate-400' : ''}`}>
            {n.comment.body ?? 'このコメントは削除されました'}
          </p>
        )}
        {n.post && n.type === 'LIKE' && (
          <div className="mt-1 flex items-center gap-2">
            {n.post.thumbnailUrl && (
              <img src={n.post.thumbnailUrl} alt="" className="size-10 shrink-0 rounded-md object-cover" />
            )}
            <p className="truncate text-sm text-slate-500">{n.post.body || '（画像のみの投稿）'}</p>
          </div>
        )}
      </div>
    </li>
  )
}
