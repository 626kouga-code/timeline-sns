import { Link, useNavigate } from 'react-router'
import type { Post } from '../api/types'
import { useAuth } from '../auth/context'
import { Avatar } from './Avatar'
import { Icon } from './Icon'
import { ImageGrid } from './ImageGrid'
import { LikeButton } from './LikeButton'
import { Menu, type MenuItem } from './Menu'
import { formatRelative } from './time'
import { useDeletePost } from './useDeletePost'

// タイムラインの 1 件。通報の操作は #31 で追加する
export function PostCard({ post }: { post: Post }) {
  const { me } = useAuth()
  const navigate = useNavigate()
  const deletePost = useDeletePost()
  const { author } = post

  const menuItems: MenuItem[] =
    me?.id === author.id ? [{ label: '削除', icon: 'trash', danger: true, onSelect: () => deletePost(post) }] : []

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
            <time dateTime={post.createdAt} className="text-slate-500">
              {formatRelative(post.createdAt)}
            </time>
          </div>
          <Menu items={menuItems} label="投稿のメニュー" />
        </div>
        {post.body && <p className="mt-0.5 break-words whitespace-pre-wrap">{post.body}</p>}
        <ImageGrid images={post.images} />
        <div className="mt-2 flex max-w-xs gap-10 text-sm text-slate-500">
          <span className="flex items-center gap-1.5" aria-label={`コメント ${post.commentCount} 件`}>
            <Icon name="comment" className="size-[18px]" />
            {post.commentCount}
          </span>
          <LikeButton post={post} />
        </div>
      </div>
    </article>
  )
}
