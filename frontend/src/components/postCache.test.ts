import { QueryClient, type InfiniteData } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { timelineKeys } from '../api/timeline'
import type { Page, Post } from '../api/types'
import { post } from '../test/utils'
import { restorePosts, updatePost } from './postCache'

const pages = (...items: Post[][]): InfiniteData<Page<Post>> => ({
  pages: items.map((page) => ({ items: page, nextCursor: null })),
  pageParams: items.map(() => null),
})

const liked = (p: Post): Post => ({ ...p, liked: true, likeCount: p.likeCount + 1 })

describe('updatePost / restorePosts', () => {
  it('タイムライン（全ページ）・プロフィールの投稿一覧・投稿詳細の同じ投稿をまとめて書き換える', () => {
    const client = new QueryClient()
    const target = post('p1', '対象')
    client.setQueryData(timelineKeys.list('global', 'u1'), pages([post('p3', '3')], [target]))
    client.setQueryData(timelineKeys.user('taro', 'u1'), pages([target]))
    client.setQueryData(['post', 'p1'], target)

    updatePost(client, 'p1', liked)

    const global = client.getQueryData<InfiniteData<Page<Post>>>(timelineKeys.list('global', 'u1'))
    expect(global?.pages[1].items[0]).toMatchObject({ liked: true, likeCount: 1 })
    expect(global?.pages[0].items[0]).toMatchObject({ id: 'p3', liked: false })
    expect(client.getQueryData<InfiniteData<Page<Post>>>(timelineKeys.user('taro', 'u1'))?.pages[0].items[0].liked).toBe(
      true,
    )
    expect(client.getQueryData<Post>(['post', 'p1'])?.likeCount).toBe(1)
  })

  it('新着件数のキャッシュ（一覧でないデータ）には触らない', () => {
    const client = new QueryClient()
    client.setQueryData(timelineKeys.newCount('global', 'u1', 'p1'), { count: 3 })

    updatePost(client, 'p1', liked)

    expect(client.getQueryData(timelineKeys.newCount('global', 'u1', 'p1'))).toEqual({ count: 3 })
  })

  it('書き換える前の状態に戻せる', () => {
    const client = new QueryClient()
    const target = post('p1', '対象')
    client.setQueryData(timelineKeys.list('home', 'u1'), pages([target]))
    client.setQueryData(['post', 'p1'], target)

    const snapshot = updatePost(client, 'p1', liked)
    restorePosts(client, snapshot)

    expect(client.getQueryData<InfiniteData<Page<Post>>>(timelineKeys.list('home', 'u1'))?.pages[0].items[0]).toEqual(
      target,
    )
    expect(client.getQueryData(['post', 'p1'])).toEqual(target)
  })
})
