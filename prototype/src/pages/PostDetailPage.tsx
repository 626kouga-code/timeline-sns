import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { CommentForm, CommentThread } from '../components/CommentThread'
import { Icon } from '../components/Icon'
import { ImageGrid } from '../components/ImageGrid'
import { Menu, type MenuItem } from '../components/Menu'
import { PageHeader } from '../components/PageHeader'
import { ReportDialog } from '../components/ReportDialog'
import { useRequireLogin } from '../components/useRequireLogin'
import { commentCount, findUser, formatDateTime, isBlockedBetween, isLiked, isPostVisible, likeCount } from '../mock/selectors'
import { useStore } from '../mock/store'

// 投稿詳細（F-13, `/posts/:id`）
export function PostDetailPage() {
  const { id } = useParams()
  const { db, me, toggleLike, deletePost, showToast } = useStore()
  const navigate = useNavigate()
  const requireLogin = useRequireLogin()
  const [reporting, setReporting] = useState(false)

  const post = db.posts.find((p) => p.id === id)
  const author = post ? findUser(db, post.userId) : undefined

  if (!post || !author || !isPostVisible(db, post, me?.id ?? null)) {
    return (
      <>
        <PageHeader title="投稿" back />
        <p className="px-4 py-16 text-center text-slate-500">この投稿は存在しないか、表示できません。</p>
      </>
    )
  }

  const liked = isLiked(db, post.id, me?.id ?? null)
  const blocked = !!me && isBlockedBetween(db, me.id, post.userId)
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
                navigate('/home')
              }
            },
          },
        ]
      : [{ label: '投稿を通報', icon: 'flag', onSelect: () => setReporting(true) }]

  return (
    <>
      <PageHeader title="投稿" back />
      <article className="border-b border-slate-100 px-4 pt-3">
        <div className="flex items-center gap-3">
          <Link to={`/users/${author.handle}`}>
            <Avatar user={author} />
          </Link>
          <div className="min-w-0 flex-1">
            <Link to={`/users/${author.handle}`} className="block truncate font-bold hover:underline">
              {author.displayName}
            </Link>
            <p className="truncate text-sm text-slate-500">@{author.handle}</p>
          </div>
          <Menu items={menuItems} label="投稿のメニュー" />
        </div>
        {post.body && <p className="mt-3 text-lg break-words whitespace-pre-wrap">{post.body}</p>}
        <ImageGrid images={post.images} />
        <p className="mt-3 border-b border-slate-100 pb-3 text-sm text-slate-500">{formatDateTime(post.createdAt)}</p>
        <div className="flex gap-5 border-b border-slate-100 py-3 text-sm">
          <span>
            <b>{likeCount(db, post.id)}</b> <span className="text-slate-500">いいね</span>
          </span>
          <span>
            <b>{commentCount(db, post.id)}</b> <span className="text-slate-500">コメント</span>
          </span>
        </div>
        <div className="flex justify-around py-1 text-slate-500">
          <button
            type="button"
            className="rounded-full p-2 hover:bg-sky-50 hover:text-sky-600"
            onClick={() => requireLogin(() => document.getElementById('comment-form')?.querySelector('textarea')?.focus())}
            aria-label="コメントする"
          >
            <Icon name="comment" className="size-6" />
          </button>
          <button
            type="button"
            className={`rounded-full p-2 hover:bg-pink-50 hover:text-pink-600 ${liked ? 'text-pink-600' : ''}`}
            onClick={() => requireLogin(() => toggleLike(post.id))}
            aria-pressed={liked}
            aria-label={liked ? 'いいねを取り消す' : 'いいね'}
            disabled={blocked}
          >
            <Icon name="heart" filled={liked} className="size-6" />
          </button>
        </div>
      </article>

      {me && !blocked && (
        <div id="comment-form" className="border-b border-slate-100 px-4 py-3">
          <CommentForm postId={post.id} parentId={null} placeholder="コメントを投稿" />
        </div>
      )}
      {!me && (
        <p className="border-b border-slate-100 px-4 py-3 text-sm text-slate-500">
          <Link to="/login" state={{ from: `/posts/${post.id}` }} className="text-sky-600 hover:underline">
            ログイン
          </Link>
          するとコメントできます
        </p>
      )}

      <CommentThread postId={post.id} />
      {reporting && <ReportDialog targetType="POST" targetId={post.id} onClose={() => setReporting(false)} />}
    </>
  )
}
