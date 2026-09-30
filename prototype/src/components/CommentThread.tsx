import { useState } from 'react'
import { Link } from 'react-router'
import { findUser, formatRelative, isBlockedBetween, isCommentVisible } from '../mock/selectors'
import { useStore } from '../mock/store'
import type { Comment, Db } from '../mock/types'
import { Avatar } from './Avatar'
import { countChars, MAX_BODY } from './text'
import { Menu, type MenuItem } from './Menu'
import { ReportDialog } from './ReportDialog'
import { useRequireLogin } from './useRequireLogin'

const byOldest = (a: Comment, b: Comment) => a.createdAt - b.createdAt

function descendantsOf(db: Db, rootId: string): Comment[] {
  const result: Comment[] = []
  const walk = (parentId: string) => {
    for (const c of db.comments.filter((c) => c.parentId === parentId)) {
      result.push(c)
      walk(c.id)
    }
  }
  walk(rootId)
  return result.sort(byOldest)
}

// 画面では 2 階層目までインデントし、それより深い返信は「@ユーザー への返信」を付けて
// 2 階層目に並べる（F-32）
export function CommentThread({ postId }: { postId: string }) {
  const { db, me } = useStore()
  const viewerId = me?.id ?? null
  const roots = db.comments
    .filter((c) => c.postId === postId && c.parentId === null && isCommentVisible(db, c, viewerId))
    .sort(byOldest)

  if (roots.length === 0) {
    return <p className="px-4 py-10 text-center text-slate-500">まだコメントはありません</p>
  }

  return (
    <div>
      {roots.map((root) => {
        const replies = descendantsOf(db, root.id).filter((c) => isCommentVisible(db, c, viewerId))
        return (
          <div key={root.id} className="border-b border-slate-100">
            <CommentItem comment={root} />
            {replies.length > 0 && (
              <div className="ml-12 border-l-2 border-slate-100">
                {replies.map((reply) => {
                  const parent = reply.parentId === root.id ? null : db.comments.find((c) => c.id === reply.parentId)
                  const parentAuthor = parent ? findUser(db, parent.userId) : undefined
                  return <CommentItem key={reply.id} comment={reply} replyTo={parentAuthor?.handle} />
                })}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

function CommentItem({ comment, replyTo }: { comment: Comment; replyTo?: string }) {
  const { db, me, deleteComment, showToast } = useStore()
  const requireLogin = useRequireLogin()
  const [replying, setReplying] = useState(false)
  const [reporting, setReporting] = useState(false)
  const author = findUser(db, comment.userId)
  if (!author) return null

  if (comment.deletedAt) {
    return (
      <div className="px-4 py-3">
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">このコメントは削除されました</p>
      </div>
    )
  }

  const post = db.posts.find((p) => p.id === comment.postId)
  const canReply = !me || !post || !isBlockedBetween(db, me.id, post.userId)
  const menuItems: MenuItem[] = !me
    ? []
    : me.id === comment.userId
      ? [
          {
            label: '削除',
            icon: 'trash',
            danger: true,
            onSelect: () => {
              if (window.confirm('このコメントを削除しますか？')) {
                deleteComment(comment.id)
                showToast('コメントを削除しました')
              }
            },
          },
        ]
      : [{ label: 'コメントを通報', icon: 'flag', onSelect: () => setReporting(true) }]

  return (
    <div className="flex gap-3 px-4 py-3">
      <Link to={`/users/${author.handle}`}>
        <Avatar user={author} size="sm" />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-1">
          <div className="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-1 text-sm">
            <Link to={`/users/${author.handle}`} className="font-bold hover:underline">
              {author.displayName}
            </Link>
            <span className="text-slate-500">@{author.handle}</span>
            <span className="text-slate-400">·</span>
            <time className="text-slate-500">{formatRelative(comment.createdAt)}</time>
          </div>
          <Menu items={menuItems} label="コメントのメニュー" />
        </div>
        {replyTo && (
          <p className="text-xs text-slate-500">
            <Link to={`/users/${replyTo}`} className="text-sky-600 hover:underline">
              @{replyTo}
            </Link>{' '}
            への返信
          </p>
        )}
        <p className="mt-0.5 break-words whitespace-pre-wrap">{comment.body}</p>
        {canReply && (
          <button
            type="button"
            onClick={() => requireLogin(() => setReplying((r) => !r))}
            className="mt-1 text-sm font-semibold text-slate-500 hover:text-sky-600"
          >
            返信
          </button>
        )}
        {replying && (
          <div className="mt-2">
            <CommentForm
              postId={comment.postId}
              parentId={comment.id}
              placeholder={`@${author.handle} に返信`}
              onDone={() => setReplying(false)}
              autoFocus
            />
          </div>
        )}
      </div>
      {reporting && <ReportDialog targetType="COMMENT" targetId={comment.id} onClose={() => setReporting(false)} />}
    </div>
  )
}

interface CommentFormProps {
  postId: string
  parentId: string | null
  placeholder: string
  onDone?: () => void
  autoFocus?: boolean
}

export function CommentForm({ postId, parentId, placeholder, onDone, autoFocus }: CommentFormProps) {
  const { me, addComment, showToast } = useStore()
  const [body, setBody] = useState('')
  if (!me) return null

  const length = countChars(body)
  const valid = body.trim().length > 0 && length <= MAX_BODY

  return (
    <form
      className="flex items-start gap-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (!valid) return
        addComment(postId, body.trim(), parentId)
        setBody('')
        showToast(parentId ? '返信しました' : 'コメントしました')
        onDone?.()
      }}
    >
      {!parentId && <Avatar user={me} />}
      <div className="flex-1">
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={placeholder}
          rows={2}
          autoFocus={autoFocus}
          className="w-full resize-none rounded-lg border border-slate-200 p-2 focus:border-sky-500 focus:outline-none"
        />
        <div className="mt-1 flex items-center justify-end gap-3">
          <span className={`text-xs tabular-nums ${length > MAX_BODY ? 'font-bold text-red-600' : 'text-slate-400'}`}>
            {length}/{MAX_BODY}
          </span>
          <button
            type="submit"
            disabled={!valid}
            className="rounded-full bg-sky-500 px-4 py-1.5 text-sm font-bold text-white hover:bg-sky-600 disabled:opacity-40"
          >
            {parentId ? '返信' : 'コメント'}
          </button>
        </div>
      </div>
    </form>
  )
}
