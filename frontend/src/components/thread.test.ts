import { describe, expect, it } from 'vitest'
import type { Comment } from '../api/types'
import { buildThreads } from './thread'

const comment = (id: string, parentId: string | null): Comment => ({
  id,
  parentId,
  body: id,
  createdAt: '2026-10-05T00:00:00Z',
  author: { id: 'u1', handle: 'taro', displayName: '太郎', avatarUrl: null },
  deleted: false,
})

describe('buildThreads', () => {
  it('ルートごとにまとめ、深い返信も 2 階層目に古い順で並べる', () => {
    const threads = buildThreads([
      comment('a', null),
      comment('a1', 'a'),
      comment('b', null),
      comment('a1x', 'a1'),
      comment('a1xy', 'a1x'),
      comment('a2', 'a'),
    ])

    expect(threads.map((t) => t.root.id)).toEqual(['a', 'b'])
    expect(threads[0].replies.map((c) => c.id)).toEqual(['a1', 'a1x', 'a1xy', 'a2'])
    expect(threads[1].replies).toEqual([])
  })

  it('親が見つからない返信は出さない', () => {
    expect(buildThreads([comment('orphan', 'gone')])).toEqual([])
  })
})
