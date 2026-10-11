import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { timelineKeys } from '../api/timeline'
import type { Post } from '../api/types'
import { json, mockApi, post, renderWithProviders, request, user } from '../test/utils'
import { useDeletePost } from './useDeletePost'

function DeleteButton({ target, onDeleted }: { target: Post; onDeleted?: () => void }) {
  const deletePost = useDeletePost()
  return (
    <button type="button" onClick={() => deletePost(target, onDeleted)}>
      削除
    </button>
  )
}

describe('useDeletePost', () => {
  it('確認でキャンセルしたら削除しない', async () => {
    const fetchMock = mockApi({})
    vi.stubGlobal('confirm', () => false)
    renderWithProviders(<DeleteButton target={post('p1', '本文')} />, { me: user })

    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('削除したら詳細のキャッシュを消し、タイムラインを取り直してトーストを出す', async () => {
    const fetchMock = mockApi({ '/api/posts/p1': () => new Response(null, { status: 204 }) })
    vi.stubGlobal('confirm', () => true)
    const onDeleted = vi.fn()
    const { queryClient } = renderWithProviders(<DeleteButton target={post('p1', '本文')} onDeleted={onDeleted} />, {
      me: user,
    })
    queryClient.setQueryData(['post', 'p1'], post('p1', '本文'))
    queryClient.setQueryData(timelineKeys.list('global', 'u1'), { pages: [], pageParams: [] })

    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(await screen.findByRole('status')).toHaveTextContent('投稿を削除しました')
    expect(request(fetchMock)).toMatchObject({ path: '/api/posts/p1', method: 'DELETE' })
    expect(onDeleted).toHaveBeenCalledOnce()
    expect(queryClient.getQueryData(['post', 'p1'])).toBeUndefined()
    expect(queryClient.getQueryState(timelineKeys.list('global', 'u1'))?.isInvalidated).toBe(true)
  })

  it('失敗したらサーバーのメッセージをトーストに出す', async () => {
    mockApi({ '/api/posts/p1': () => json(403, { code: 'FORBIDDEN', detail: 'この操作を行う権限がありません' }) })
    vi.stubGlobal('confirm', () => true)
    const onDeleted = vi.fn()
    renderWithProviders(<DeleteButton target={post('p1', '本文')} onDeleted={onDeleted} />, { me: user })

    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(await screen.findByRole('status')).toHaveTextContent('この操作を行う権限がありません')
    expect(onDeleted).not.toHaveBeenCalled()
  })
})
