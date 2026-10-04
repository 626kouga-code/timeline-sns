import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { deletePost } from '../api/posts'
import { timelineKeys } from '../api/timeline'
import type { Post } from '../api/types'
import { useToast } from './toast'

/**
 * 投稿を確認のうえ削除する関数を返す（F-12）。
 * onDeleted は削除の成功後、タイムラインを取り直す前に呼ばれる（詳細画面から離れるのに使う）。
 */
export function useDeletePost() {
  const queryClient = useQueryClient()
  const showToast = useToast()
  const mutation = useMutation({ mutationFn: (post: Post) => deletePost(post.id) })

  return (post: Post, onDeleted?: () => void) => {
    if (!window.confirm('この投稿を削除しますか？いいね・コメントも削除されます。')) return
    mutation.mutate(post, {
      onSuccess: () => {
        onDeleted?.()
        queryClient.removeQueries({ queryKey: ['post', post.id] })
        void queryClient.invalidateQueries({ queryKey: timelineKeys.all })
        showToast('投稿を削除しました')
      },
      onError: (error) => {
        showToast(error instanceof ApiError ? error.message : '投稿を削除できませんでした')
      },
    })
  }
}
