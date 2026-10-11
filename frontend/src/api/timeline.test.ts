import { describe, expect, it } from 'vitest'
import { json, mockApi, page, post, request } from '../test/utils'
import { fetchNewCount, fetchTimeline, timelineKeys } from './timeline'

describe('タイムラインの API', () => {
  it('最初のページは cursor なしで取る', async () => {
    const fetchMock = mockApi({ '/api/timeline/global': () => page([post('p2', '2'), post('p1', '1')], 'p1') })

    const result = await fetchTimeline('global', null)

    expect(result.items.map((p) => p.id)).toEqual(['p2', 'p1'])
    expect(result.nextCursor).toBe('p1')
    expect(request(fetchMock).path).toBe('/api/timeline/global')
  })

  it('次のページは cursor を付けて取る', async () => {
    const fetchMock = mockApi({ '/api/timeline/home?cursor=p1': () => page([]) })

    await expect(fetchTimeline('home', 'p1')).resolves.toEqual({ items: [], nextCursor: null })
    expect(request(fetchMock).path).toBe('/api/timeline/home?cursor=p1')
  })

  it('新着件数は since を付けて取る', async () => {
    const fetchMock = mockApi({ '/api/timeline/global/new-count?since=p9': () => json(200, { count: 3 }) })

    await expect(fetchNewCount('global', 'p9')).resolves.toEqual({ count: 3 })
    expect(request(fetchMock).path).toBe('/api/timeline/global/new-count?since=p9')
  })

  it('キャッシュのキーはすべて timeline の下にあり、まとめて取り直せる', () => {
    for (const key of [
      timelineKeys.list('home', 'u1'),
      timelineKeys.user('Taro', 'u1'),
      timelineKeys.newCount('global', 'guest', 'p1'),
    ]) {
      expect(key[0]).toBe(timelineKeys.all[0])
    }
    // ハンドルは大文字小文字を区別しない
    expect(timelineKeys.user('Taro', 'u1')).toEqual(timelineKeys.user('taro', 'u1'))
  })
})
