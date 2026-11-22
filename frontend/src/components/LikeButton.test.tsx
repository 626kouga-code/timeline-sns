import { useQuery } from '@tanstack/react-query'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Post } from '../api/types'
import { deferred, json, mockApi, post, renderWithProviders, request, user } from '../test/utils'
import { LikeButton } from './LikeButton'

// 実際の画面と同じく、キャッシュ（投稿詳細）にある投稿を表示する。楽観的更新はキャッシュを書き換える
function CachedLikeButton({ id }: { id: string }) {
  const { data } = useQuery<Post>({ queryKey: ['post', id], queryFn: () => new Promise(() => {}), staleTime: Infinity })
  return data ? <LikeButton post={data} /> : null
}

function setup(initial: Post, me: typeof user | null = user) {
  const result = renderWithProviders(<CachedLikeButton id={initial.id} />, { me, path: `/posts/${initial.id}` })
  result.queryClient.setQueryData(['post', initial.id], initial)
  return result
}

const button = () => screen.getByRole('button', { name: /いいね/ })

describe('LikeButton', () => {
  it('いいねの数と状態を表示する', async () => {
    setup({ ...post('p1', '本文'), likeCount: 3, liked: true })

    expect(await screen.findByRole('button', { name: '3 件のいいね', pressed: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('押した瞬間に表示を変え、サーバーの応答の数で確定する', async () => {
    const response = deferred<Response>()
    const fetchMock = mockApi({ '/api/posts/p1/likes': () => response.promise })
    setup(post('p1', '本文'))

    await userEvent.click(await screen.findByRole('button', { name: '0 件のいいね', pressed: false }))

    // 応答を待たずに変わる
    expect(button()).toHaveAccessibleName('1 件のいいね')
    expect(request(fetchMock)).toMatchObject({ path: '/api/posts/p1/likes', method: 'POST' })

    // 他の人のいいねも反映された数になる
    response.resolve(json(200, { liked: true, likeCount: 5 }))
    await screen.findByRole('button', { name: '5 件のいいね', pressed: true })
  })

  it('いいね済みなら取り消しを送る', async () => {
    const fetchMock = mockApi({ '/api/posts/p1/likes': () => json(200, { liked: false, likeCount: 0 }) })
    setup({ ...post('p1', '本文'), likeCount: 1, liked: true })

    await userEvent.click(await screen.findByRole('button', { name: '1 件のいいね', pressed: true }))

    expect(request(fetchMock).method).toBe('DELETE')
    await screen.findByRole('button', { name: '0 件のいいね', pressed: false })
  })

  it('失敗したら元の表示に戻し、理由をトーストで出す', async () => {
    mockApi({
      '/api/posts/p1/likes': () => json(404, { code: 'POST_NOT_FOUND', detail: 'この投稿は存在しないか、表示できません' }),
    })
    setup(post('p1', '本文'))

    await userEvent.click(await screen.findByRole('button', { name: '0 件のいいね', pressed: false }))

    expect(await screen.findByRole('status')).toHaveTextContent('この投稿は存在しないか、表示できません')
    expect(button()).toHaveAccessibleName('0 件のいいね')
    expect(button()).toHaveAttribute('aria-pressed', 'false')
  })

  it('送信中に連打されても 1 回しか送らない', async () => {
    const response = deferred<Response>()
    const fetchMock = mockApi({ '/api/posts/p1/likes': () => response.promise })
    setup(post('p1', '本文'))

    await userEvent.click(await screen.findByRole('button', { name: '0 件のいいね', pressed: false }))
    await userEvent.click(button())
    await userEvent.click(button())

    expect(fetchMock).toHaveBeenCalledOnce()
    response.resolve(json(200, { liked: true, likeCount: 1 }))
  })

  it('未ログインならログイン画面へ誘導し、送らない', async () => {
    const fetchMock = mockApi({})
    setup(post('p1', '本文'), null)

    await userEvent.click(await screen.findByRole('button', { name: '0 件のいいね', pressed: false }))

    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
    expect(screen.getByTestId('location')).toHaveAttribute('data-from', '/posts/p1')
  })
})
