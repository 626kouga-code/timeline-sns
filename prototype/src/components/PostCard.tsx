import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { commentCount, findUser, formatRelative, isLiked, likeCount } from '../mock/selectors'
import { useStore } from '../mock/store'
import type { Post } from '../mock/types'
import { Avatar } from './Avatar'
import { Icon } from './Icon'
import { ImageGrid } from './ImageGrid'
import { Menu, type MenuItem } from './Menu'
import { ReportDialog } from './ReportDialog'
import { useRequireLogin } from './useRequireLogin'

export function PostCard({ post }: { post: Post }) {
  const { db, me, toggleLike, deletePost, showToast } = useStore()
  const navigate = useNavigate()
  const requireLogin = useRequireLogin()
  const [reporting, setReporting] = useState(false)
  const author = findUser(db, post.userId)
  if (!author) return null

  const liked = isLiked(db, post.id, me?.id ?? null)
  const menuItems: MenuItem[] = !me
    ? []
    : me.id === post.userId
      ? [
          {
            label: '削除',
            icon: 'trash',
            danger: true,
            onSelect: () => {
              if (window.confirm('この投稿を削除しますか？画像・いいね・コメントも削除されます。')) {
                deletePost(post.id)
                showToast('投稿を削除しました')
              }
            },
          },
        ]
      : [{ label: '投稿を通報', icon: 'flag', onSelect: () => setReporting(true) }]

  return (
    <article
      className="flex cursor-pointer gap-3 border-b border-slate-100 px-4 py-3 hover:bg-slate-50/70"
      onClick={() => navigate(`/posts/${post.id}`)}
    >
      <Link to={`/users/${author.handle}`} onClick={(e) => e.stopPropagation()}>
        <Avatar user={author} />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-1">
          <div className="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-1 text-sm">
            <Link
              to={`/users/${author.handle}`}
              className="truncate font-bold hover:underline"
              onClick={(e) => e.stopPropagation()}
            >
              {author.displayName}
            </Link>
            <span className="truncate text-slate-500">@{author.handle}</span>
            <span className="text-slate-400">·</span>
            <time className="text-slate-500">{formatRelative(post.createdAt)}</time>
          </div>
          <Menu items={menuItems} label="投稿のメニュー" />
        </div>
        {post.body && <p className="mt-0.5 break-words whitespace-pre-wrap">{post.body}</p>}
        <ImageGrid images={post.images} />
        <div className="mt-2 flex max-w-xs gap-10 text-sm text-slate-500">
          <span className="flex items-center gap-1.5 hover:text-sky-600">
            <Icon name="comment" className="size-[18px]" />
            {commentCount(db, post.id)}
          </span>
          <button
            type="button"
            className={`flex items-center gap-1.5 hover:text-pink-600 ${liked ? 'text-pink-600' : ''}`}
            onClick={(e) => {
              e.stopPropagation()
              requireLogin(() => toggleLike(post.id))
            }}
            aria-pressed={liked}
            aria-label={liked ? 'いいねを取り消す' : 'いいね'}
          >
            <Icon name="heart" filled={liked} className="size-[18px]" />
            {likeCount(db, post.id)}
          </button>
        </div>
      </div>
      {reporting && (
        <div onClick={(e) => e.stopPropagation()}>
          <ReportDialog targetType="POST" targetId={post.id} onClose={() => setReporting(false)} />
        </div>
      )}
    </article>
  )
}
