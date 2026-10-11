import { describe, expect, it } from 'vitest'
import { json, mockApi, request } from '../test/utils'
import { like, unlike } from './likes'

describe('いいねの API', () => {
  it('like は POST を送り、最新のいいね状態を返す', async () => {
    const fetchMock = mockApi({ '/api/posts/p1/likes': () => json(200, { liked: true, likeCount: 3 }) })

    await expect(like('p1')).resolves.toEqual({ liked: true, likeCount: 3 })
    expect(request(fetchMock).method).toBe('POST')
  })

  it('unlike は DELETE を送る', async () => {
    const fetchMock = mockApi({ '/api/posts/p1/likes': () => json(200, { liked: false, likeCount: 2 }) })

    await expect(unlike('p1')).resolves.toEqual({ liked: false, likeCount: 2 })
    expect(request(fetchMock).method).toBe('DELETE')
  })

  it('見えない投稿へのいいねは 404 のエラーになる', async () => {
    mockApi({
      '/api/posts/p1/likes': () => json(404, { code: 'POST_NOT_FOUND', detail: 'この投稿は存在しないか、表示できません' }),
    })

    await expect(like('p1')).rejects.toMatchObject({ status: 404, code: 'POST_NOT_FOUND' })
  })
})
