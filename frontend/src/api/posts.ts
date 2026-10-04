import { apiFetch } from './client'
import type { Post } from './types'

/** 投稿作成（F-10）。画像（#14）を足せるよう multipart で送る */
export function createPost(body: string): Promise<Post> {
  const form = new FormData()
  form.append('body', body)
  return apiFetch<Post>('/api/posts', { method: 'POST', body: form })
}

/** 投稿詳細（F-13） */
export function getPost(id: string): Promise<Post> {
  return apiFetch<Post>(`/api/posts/${encodeURIComponent(id)}`)
}

/** 投稿削除（F-12）。本人の投稿だけ削除できる */
export function deletePost(id: string): Promise<void> {
  return apiFetch<void>(`/api/posts/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
