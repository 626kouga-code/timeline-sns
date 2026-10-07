import { apiFetch } from './client'
import type { Post } from './types'

export interface PostInput {
  body: string
  /** 0〜4 枚。画像があれば本文は空でもよい */
  images: File[]
}

/** 投稿作成（F-10・F-11）。画像は images パートとして multipart で送る */
export function createPost({ body, images }: PostInput): Promise<Post> {
  const form = new FormData()
  form.append('body', body)
  for (const image of images) form.append('images', image)
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
