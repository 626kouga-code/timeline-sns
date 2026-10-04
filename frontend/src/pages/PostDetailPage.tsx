import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { getPost } from '../api/posts'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { Menu, type MenuItem } from '../components/Menu'
import { PageHeader } from '../components/PageHeader'
import { formatDateTime } from '../components/time'
import { useDeletePost } from '../components/useDeletePost'

// 投稿詳細（F-13, `/posts/:id`）。コメントスレッドは #21〜#23 で追加する
export function PostDetailPage() {
  const { id = '' } = useParams()
  const { me } = useAuth()
  const navigate = useNavigate()
  const deletePost = useDeletePost()
  const query = useQuery({
    queryKey: ['post', id, me?.id ?? 'guest'],
    queryFn: () => getPost(id),
  })

  if (query.isPending) {
    return (
      <>
        <PageHeader title="投稿" back />
        <p className="py-10 text-center text-sm text-slate-400">読み込み中…</p>
      </>
    )
  }

  if (query.isError) {
    return (
      <>
        <PageHeader title="投稿" back />
        <p role="alert" className="px-4 py-16 text-center text-slate-500">
          {query.error.message}
        </p>
      </>
    )
  }

  const post = query.data
  const { author } = post
  const menuItems: MenuItem[] =
    me?.id === author.id
      ? [{ label: '削除', icon: 'trash', danger: true, onSelect: () => deletePost(post, () => navigate('/home')) }]
      : []

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
        <p className="mt-3 border-b border-slate-100 pb-3 text-sm text-slate-500">
          <time dateTime={post.createdAt}>{formatDateTime(post.createdAt)}</time>
        </p>
        <div className="flex gap-5 py-3 text-sm">
          <span>
            <b>{post.likeCount}</b> <span className="text-slate-500">いいね</span>
          </span>
          <span>
            <b>{post.commentCount}</b> <span className="text-slate-500">コメント</span>
          </span>
        </div>
      </article>
      <p className="px-4 py-8 text-center text-sm text-slate-400">コメント機能は準備中です</p>
    </>
  )
}
