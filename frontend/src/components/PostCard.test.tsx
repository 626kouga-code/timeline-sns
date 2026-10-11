import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockApi, post, renderWithProviders, request, user } from '../test/utils'
import { PostCard } from './PostCard'

const hanako = { id: 'u2', handle: 'hanako', displayName: '花子' }

describe('PostCard', () => {
  it('作者・本文・相対時刻・コメント数・いいねを出す', () => {
    const target = { ...post('p1', 'こんにちは\n2 行目', hanako), commentCount: 4, likeCount: 2 }
    renderWithProviders(<PostCard post={target} />)

    const card = screen.getByRole('article')
    expect(within(card).getByRole('link', { name: '花子' })).toHaveAttribute('href', '/users/hanako')
    expect(card).toHaveTextContent('@hanako')
    expect(card).toHaveTextContent('こんにちは 2 行目')
    expect(within(card).getByText('たった今')).toHaveAttribute('dateTime', target.createdAt)
    expect(within(card).getByLabelText('コメント 4 件')).toBeInTheDocument()
    expect(within(card).getByRole('button', { name: 'いいね（2 件）' })).toBeInTheDocument()
  })

  it('本文が空なら本文の段落を出さず、画像を出す', () => {
    const target = {
      ...post('p1', ''),
      images: [{ url: '/api/media/1.jpg', thumbnailUrl: '/api/media/1-t.jpg', width: 10, height: 10 }],
    }
    renderWithProviders(<PostCard post={target} />)

    expect(screen.getByRole('button', { name: '画像 1 を拡大' })).toBeInTheDocument()
  })

  it('カードを押すと投稿詳細へ、作者を押すとプロフィールへ移る', async () => {
    const { unmount } = renderWithProviders(<PostCard post={post('p1', '本文', hanako)} />)
    await userEvent.click(screen.getByText('本文'))
    expect(screen.getByTestId('location')).toHaveTextContent('/posts/p1')
    unmount()

    renderWithProviders(<PostCard post={post('p1', '本文', hanako)} />)
    await userEvent.click(screen.getByRole('link', { name: '花子' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/users/hanako')
  })

  it('他人の投稿にはメニューを出さない', () => {
    renderWithProviders(<PostCard post={post('p1', '本文', hanako)} />, { me: user })

    expect(screen.queryByRole('button', { name: '投稿のメニュー' })).not.toBeInTheDocument()
  })

  it('自分の投稿はメニューから削除でき、カードのクリックにはならない', async () => {
    const fetchMock = mockApi({ '/api/posts/p1': () => new Response(null, { status: 204 }) })
    vi.stubGlobal('confirm', () => true)
    renderWithProviders(<PostCard post={post('p1', '自分の投稿')} />, { me: user, path: '/home' })

    await userEvent.click(screen.getByRole('button', { name: '投稿のメニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(await screen.findByRole('status')).toHaveTextContent('投稿を削除しました')
    expect(request(fetchMock)).toMatchObject({ path: '/api/posts/p1', method: 'DELETE' })
    expect(screen.getByTestId('location')).toHaveTextContent('/home')
  })
})
