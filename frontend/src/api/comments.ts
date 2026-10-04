import { apiFetch } from './client'
import type { Comment } from './types'

/** ブロックの除外は閲覧者で変わるので、キーに閲覧者を含める。作成・削除のあとは `all` で取り直す */
export const commentKeys = {
  all: (postId: string) => ['comments', postId] as const,
  list: (postId: string, viewer: string) => ['comments', postId, viewer] as const,
}

/** 投稿のコメントを古い順に全件取る（1 回の問い合わせでスレッド全体） */
export function fetchComments(postId: string): Promise<Comment[]> {
  return apiFetch<Comment[]>(`/api/posts/${encodeURIComponent(postId)}/comments`)
}

/** コメント（F-31）。parentId を渡すと返信（F-32） */
export function createComment(postId: string, body: string, parentId: string | null): Promise<Comment> {
  return apiFetch<Comment>(`/api/posts/${encodeURIComponent(postId)}/comments`, {
    method: 'POST',
    body: { body, parentId },
  })
}

/** コメント削除（F-33）。本人のコメントだけ削除できる */
export function deleteComment(id: string): Promise<void> {
  return apiFetch<void>(`/api/comments/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
