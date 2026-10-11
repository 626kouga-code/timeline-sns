import { describe, expect, it } from 'vitest'
import { json, mockApi, request } from '../test/utils'
import { commentKeys, createComment, deleteComment, fetchComments } from './comments'
import type { Comment } from './types'

const comment: Comment = {
  id: 'c1',
  parentId: null,
  body: 'いいね',
  createdAt: '2026-10-05T00:00:00Z',
  author: { id: 'u2', handle: 'hanako', displayName: '花子', avatarUrl: null },
  deleted: false,
}

describe('コメントの API', () => {
  it('fetchComments は投稿のコメントを配列で受け取る', async () => {
    const fetchMock = mockApi({ '/api/posts/p1/comments': () => json(200, [comment]) })

    await expect(fetchComments('p1')).resolves.toEqual([comment])
    expect(request(fetchMock).method).toBe('GET')
  })

  it('createComment は本文と返信先を JSON で送る', async () => {
    const fetchMock = mockApi({ '/api/posts/p1/comments': () => json(201, { ...comment, parentId: 'c0' }) })

    await createComment('p1', '返信です', 'c0')

    const sent = request(fetchMock)
    expect(sent.method).toBe('POST')
    expect(JSON.parse(sent.body as string)).toEqual({ body: '返信です', parentId: 'c0' })
  })

  it('返信でないコメントは parentId を null で送る', async () => {
    const fetchMock = mockApi({ '/api/posts/p1/comments': () => json(201, comment) })

    await createComment('p1', 'いいね', null)

    expect(JSON.parse(request(fetchMock).body as string)).toEqual({ body: 'いいね', parentId: null })
  })

  it('deleteComment はコメントの URL に DELETE を送る', async () => {
    const fetchMock = mockApi({ '/api/comments/c1': () => new Response(null, { status: 204 }) })

    await expect(deleteComment('c1')).resolves.toBeUndefined()
    expect(request(fetchMock)).toMatchObject({ path: '/api/comments/c1', method: 'DELETE' })
  })

  it('キャッシュのキーは閲覧者ごとに分かれ、all でまとめて取り直せる', () => {
    expect(commentKeys.list('p1', 'u1')).toEqual(['comments', 'p1', 'u1'])
    expect(commentKeys.list('p1', 'u1').slice(0, 2)).toEqual([...commentKeys.all('p1')])
  })
})
