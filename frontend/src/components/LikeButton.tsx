import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { like, unlike } from '../api/likes'
import { timelineKeys } from '../api/timeline'
import type { Post } from '../api/types'
import { Icon } from './Icon'
import { restorePosts, updatePost } from './postCache'
import { useToast } from './toast'
import { useRequireLogin } from './useRequireLogin'

/**
 * いいねボタン（F-30）。押した瞬間に表示を変え（楽観的更新）、失敗したら元に戻す。
 * いいね数・状態はタイムラインの応答に含まれているので、投稿ごとに問い合わせない。
 */
export function LikeButton({ post, size = 'sm' }: { post: Post; size?: 'sm' | 'lg' }) {
  const queryClient = useQueryClient()
  const showToast = useToast()
  const requireLogin = useRequireLogin()

  const mutation = useMutation({
    mutationFn: (next: boolean) => (next ? like(post.id) : unlike(post.id)),
    onMutate: async (next) => {
      // 取得中の古いデータで書き換えた表示が上書きされないよう、先に止める
      await Promise.all([
        queryClient.cancelQueries({ queryKey: timelineKeys.all }),
        queryClient.cancelQueries({ queryKey: ['post', post.id] }),
      ])
      return updatePost(queryClient, post.id, (p) => ({
        ...p,
        liked: next,
        likeCount: Math.max(0, p.likeCount + (next ? 1 : -1)),
      }))
    },
    // 他の人のいいねも反映された数で確定させる
    onSuccess: (state) => {
      updatePost(queryClient, post.id, (p) => ({ ...p, ...state }))
    },
    onError: (error, _next, snapshot) => {
      if (snapshot) restorePosts(queryClient, snapshot)
      showToast(error instanceof ApiError ? error.message : 'いいねできませんでした')
    },
  })

  const iconSize = size === 'lg' ? 'size-6' : 'size-[18px]'

  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation()
        // 応答を待たずに連打されると順序が入れ替わるので、送信中は受け付けない
        if (mutation.isPending) return
        requireLogin(() => mutation.mutate(!post.liked))
      }}
      className={`flex items-center gap-1.5 hover:text-pink-600 ${post.liked ? 'text-pink-600' : ''}`}
      aria-pressed={post.liked}
      aria-label={`${post.liked ? 'いいねを取り消す' : 'いいね'}（${post.likeCount} 件）`}
    >
      <Icon name="heart" filled={post.liked} className={iconSize} />
      {post.likeCount}
    </button>
  )
}
