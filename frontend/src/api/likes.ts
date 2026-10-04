import { apiFetch } from './client'
import type { LikeState } from './types'

/** いいね（F-30）。冪等なので二重に送っても状態は変わらない */
export function like(postId: string): Promise<LikeState> {
  return apiFetch<LikeState>(`/api/posts/${encodeURIComponent(postId)}/likes`, { method: 'POST' })
}

/** いいねの取り消し（F-30） */
export function unlike(postId: string): Promise<LikeState> {
  return apiFetch<LikeState>(`/api/posts/${encodeURIComponent(postId)}/likes`, { method: 'DELETE' })
}
