import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { commentKeys, createComment, deleteComment, fetchComments } from '../api/comments'
import { timelineKeys } from '../api/timeline'
import type { Comment } from '../api/types'
import { useAuth } from '../auth/context'
import { Avatar } from './Avatar'
import { Menu, type MenuItem } from './Menu'
import { countChars, MAX_BODY } from './text'
import { buildThreads } from './thread'
import { formatRelative } from './time'
import { useToast } from './toast'
import { useRequireLogin } from './useRequireLogin'

/** 投稿詳細のコメント欄。スレッド全体を 1 回の問い合わせで取る（返信ごとに API を呼ばない） */
export function CommentThread({ postId }: { postId: string }) {
  const { me } = useAuth()
  const query = useQuery({
    queryKey: commentKeys.list(postId, me?.id ?? 'guest'),
    queryFn: () => fetchComments(postId),
  })

  if (query.isPending) return <p className="py-8 text-center text-sm text-slate-400">コメントを読み込み中…</p>
  if (query.isError) {
    return (
      <p role="alert" className="px-4 py-8 text-center text-sm text-slate-500">
        {query.error.message}
      </p>
    )
  }

  const byId = new Map(query.data.map((c) => [c.id, c]))
  const threads = buildThreads(query.data)
  if (threads.length === 0) {
    return <p className="px-4 py-10 text-center text-slate-500">まだコメントはありません</p>
  }

  return (
    <div>
      {threads.map(({ root, replies }) => (
        <div key={root.id} className="border-b border-slate-100">
          <CommentItem comment={root} postId={postId} />
          {replies.length > 0 && (
            <div className="ml-12 border-l-2 border-slate-100">
              {replies.map((reply) => (
                <CommentItem
                  key={reply.id}
                  comment={reply}
                  postId={postId}
                  // 2 階層目より深い返信には、誰への返信かを付ける
                  replyTo={reply.parentId === root.id ? undefined : (byId.get(reply.parentId ?? '') ?? null)}
                />
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

/** コメントの作成・削除のあとに取り直すもの（コメント欄と、投稿のコメント数） */
function useRefreshAfterChange(postId: string) {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: commentKeys.all(postId) })
    void queryClient.invalidateQueries({ queryKey: ['post', postId] })
    void queryClient.invalidateQueries({ queryKey: timelineKeys.all })
  }
}

interface CommentItemProps {
  comment: Comment
  postId: string
  /** 2 階層目より深い返信の返信先。undefined なら表示しない、null は返信先が見つからない */
  replyTo?: Comment | null
}

function CommentItem({ comment, postId, replyTo }: CommentItemProps) {
  const { me } = useAuth()
  const showToast = useToast()
  const requireLogin = useRequireLogin()
  const refresh = useRefreshAfterChange(postId)
  const [replying, setReplying] = useState(false)
  const remove = useMutation({
    mutationFn: () => deleteComment(comment.id),
    onSuccess: () => {
      refresh()
      showToast('コメントを削除しました')
    },
    onError: (error) => showToast(error instanceof ApiError ? error.message : 'コメントを削除できませんでした'),
  })

  const { author } = comment
  if (comment.deleted || !author) {
    return (
      <div className="px-4 py-3">
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">このコメントは削除されました</p>
      </div>
    )
  }

  const menuItems: MenuItem[] =
    me?.id === author.id
      ? [
          {
            label: '削除',
            icon: 'trash',
            danger: true,
            onSelect: () => {
              if (window.confirm('このコメントを削除しますか？')) remove.mutate()
            },
          },
        ]
      : []

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
            <time dateTime={comment.createdAt} className="text-slate-500">
              {formatRelative(comment.createdAt)}
            </time>
          </div>
          <Menu items={menuItems} label="コメントのメニュー" />
        </div>
        {replyTo !== undefined && (
          <p className="text-xs text-slate-500">
            {replyTo?.author ? (
              <>
                <Link to={`/users/${replyTo.author.handle}`} className="text-sky-600 hover:underline">
                  @{replyTo.author.handle}
                </Link>{' '}
                への返信
              </>
            ) : (
              '削除されたコメントへの返信'
            )}
          </p>
        )}
        <p className="mt-0.5 break-words whitespace-pre-wrap">{comment.body}</p>
        <button
          type="button"
          onClick={() => requireLogin(() => setReplying((r) => !r))}
          className="mt-1 text-sm font-semibold text-slate-500 hover:text-sky-600"
        >
          返信
        </button>
        {replying && (
          <div className="mt-2">
            <CommentForm
              postId={postId}
              parentId={comment.id}
              placeholder={`@${author.handle} に返信`}
              onDone={() => setReplying(false)}
              autoFocus
            />
          </div>
        )}
      </div>
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
  const { me } = useAuth()
  const showToast = useToast()
  const refresh = useRefreshAfterChange(postId)
  const [body, setBody] = useState('')
  const mutation = useMutation({
    mutationFn: (text: string) => createComment(postId, text, parentId),
    onSuccess: () => {
      refresh()
      showToast(parentId ? '返信しました' : 'コメントしました')
      onDone?.()
    },
    // 送った本文を戻す（送信後に入力し始めた文章があれば、それを優先して消さない）
    onError: (_error, text) => setBody((current) => current || text),
  })
  if (!me) return null

  const length = countChars(body)
  const valid = body.trim().length > 0 && length <= MAX_BODY && !mutation.isPending
  const error = mutation.error instanceof ApiError ? (mutation.error.errors.body ?? mutation.error.message) : null

  return (
    <form
      className="flex items-start gap-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (!valid) return
        // 送った時点で入力欄を空ける。応答を待ってから消すと、その間に打った文章まで消してしまう
        mutation.mutate(body.trim())
        setBody('')
      }}
    >
      {!parentId && <Avatar user={me} />}
      <div className="flex-1">
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={placeholder}
          aria-label={parentId ? '返信の本文' : 'コメントの本文'}
          rows={2}
          autoFocus={autoFocus}
          className="w-full resize-none rounded-lg border border-slate-200 p-2 focus:border-sky-500 focus:outline-none"
        />
        {error && (
          <p role="alert" className="text-sm text-red-600">
            {error}
          </p>
        )}
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
