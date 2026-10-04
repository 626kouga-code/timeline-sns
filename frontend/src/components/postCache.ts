import type { InfiniteData, QueryClient, QueryKey } from '@tanstack/react-query'
import { timelineKeys } from '../api/timeline'
import type { Page, Post } from '../api/types'

type Snapshot = [QueryKey, unknown][]

const isTimelinePages = (data: unknown): data is InfiniteData<Page<Post>> =>
  typeof data === 'object' && data !== null && 'pages' in data

/**
 * キャッシュ中の投稿 1 件を、タイムライン（全ページ）と投稿詳細のすべてで書き換える。
 * API は呼ばない。戻り値は書き換え前の状態で、{@link restorePosts} に渡すと元に戻せる。
 */
export function updatePost(queryClient: QueryClient, postId: string, update: (post: Post) => Post): Snapshot {
  const timelines = queryClient.getQueriesData({ queryKey: timelineKeys.all })
  const details = queryClient.getQueriesData({ queryKey: ['post', postId] })

  for (const [key, data] of timelines) {
    // 新着件数（['timeline', …, 'new-count']）も同じキーの下にあるので、一覧だけ書き換える
    if (!isTimelinePages(data)) continue
    queryClient.setQueryData<InfiniteData<Page<Post>>>(key, {
      ...data,
      pages: data.pages.map((page) => ({
        ...page,
        items: page.items.map((post) => (post.id === postId ? update(post) : post)),
      })),
    })
  }
  for (const [key, data] of details) {
    if (data) queryClient.setQueryData<Post>(key, update(data as Post))
  }
  return [...timelines, ...details]
}

export function restorePosts(queryClient: QueryClient, snapshot: Snapshot) {
  for (const [key, data] of snapshot) queryClient.setQueryData(key, data)
}
