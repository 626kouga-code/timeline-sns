import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Icon, type IconName } from '../components/Icon'
import { PageHeader } from '../components/PageHeader'
import { findUser, formatRelative, isBlockedBetween } from '../mock/selectors'
import { useStore } from '../mock/store'
import type { NotificationType } from '../mock/types'

const TYPE_INFO: Record<NotificationType, { icon: IconName; color: string; text: string }> = {
  LIKE: { icon: 'heart', color: 'text-pink-600', text: 'があなたの投稿にいいねしました' },
  COMMENT: { icon: 'comment', color: 'text-sky-600', text: 'があなたの投稿にコメントしました' },
  REPLY: { icon: 'comment', color: 'text-emerald-600', text: 'があなたのコメントに返信しました' },
  FOLLOW: { icon: 'user', color: 'text-violet-600', text: 'にフォローされました' },
}

// 通知一覧（F-50, `/notifications`）。開いたら既読にする（F-51）
export function NotificationsPage() {
  const { db, me, markNotificationsRead } = useStore()
  const navigate = useNavigate()
  // 既読化する前の未読 id を覚えておき、この画面を開いている間は未読として強調表示する
  const [unreadIds] = useState(
    () => new Set(db.notifications.filter((n) => n.recipientId === me?.id && !n.readAt).map((n) => n.id)),
  )

  useEffect(() => {
    markNotificationsRead()
    // 画面を開いたときに 1 回だけ既読化する
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!me) return null

  const list = db.notifications
    .filter((n) => n.recipientId === me.id && !isBlockedBetween(db, me.id, n.actorId))
    .sort((a, b) => b.createdAt - a.createdAt)

  return (
    <>
      <PageHeader title="通知" />
      {list.length === 0 && <p className="px-4 py-16 text-center text-slate-500">まだ通知はありません</p>}
      {list.map((n) => {
        const actor = findUser(db, n.actorId)
        if (!actor) return null
        const info = TYPE_INFO[n.type]
        const post = n.postId ? db.posts.find((p) => p.id === n.postId) : undefined
        const comment = n.commentId ? db.comments.find((c) => c.id === n.commentId) : undefined
        const to = post ? `/posts/${post.id}` : `/users/${actor.handle}`
        return (
          <div
            key={n.id}
            onClick={() => navigate(to)}
            className={`flex cursor-pointer gap-3 border-b border-slate-100 px-4 py-3 hover:bg-slate-50 ${
              unreadIds.has(n.id) ? 'bg-sky-50/60' : ''
            }`}
          >
            <Icon name={info.icon} filled={n.type === 'LIKE'} className={`mt-1 size-6 shrink-0 ${info.color}`} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between">
                <Link to={`/users/${actor.handle}`} onClick={(e) => e.stopPropagation()}>
                  <Avatar user={actor} size="sm" />
                </Link>
                <span className="flex items-center gap-2 text-xs text-slate-500">
                  {unreadIds.has(n.id) && <span className="size-2 rounded-full bg-sky-500" aria-label="未読" />}
                  {formatRelative(n.createdAt)}
                </span>
              </div>
              <p className="mt-1.5 text-sm">
                <b>{actor.displayName}</b>
                <span className="text-slate-500"> さん</span>
                {info.text}
              </p>
              {comment && !comment.deletedAt && <p className="mt-1 text-sm">{comment.body}</p>}
              {post && (n.type === 'LIKE' || !comment) && (
                <p className="mt-1 truncate text-sm text-slate-500">{post.body || '（画像のみの投稿）'}</p>
              )}
            </div>
          </div>
        )
      })}
    </>
  )
}
