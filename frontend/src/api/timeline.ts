import { apiFetch } from './client'
import type { Page, Post } from './types'

/** global = 全体タイムライン（F-21）、home = ホームタイムライン（F-20） */
export type TimelineMode = 'global' | 'home'

/**
 * TanStack Query のキー。投稿の作成・削除のあとは `timelineKeys.all` でまとめて取り直す。
 * いいね状態やブロックの除外は閲覧者で変わるので、キーに閲覧者を含める。
 */
export const timelineKeys = {
  all: ['timeline'] as const,
  list: (mode: TimelineMode, viewer: string) => ['timeline', mode, viewer] as const,
  newCount: (mode: TimelineMode, viewer: string, since: string | undefined) =>
    ['timeline', mode, viewer, 'new-count', since] as const,
}

/** タイムラインの 1 ページ。cursor には前のページの nextCursor を渡す */
export function fetchTimeline(mode: TimelineMode, cursor: string | null): Promise<Page<Post>> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  return apiFetch<Page<Post>>(`/api/timeline/${mode}${query}`)
}

/** since（表示中の先頭の投稿）より新しい投稿の数（F-22）。自分の投稿は数えず、100 件で打ち切る */
export function fetchNewCount(mode: TimelineMode, since: string): Promise<{ count: number }> {
  return apiFetch<{ count: number }>(`/api/timeline/${mode}/new-count?since=${encodeURIComponent(since)}`)
}
