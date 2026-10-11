import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { timelineKeys } from '../api/timeline'
import { userKeys } from '../api/users'
import { deferred, json, mockApi, renderWithProviders, request, user } from '../test/utils'
import { FollowButton } from './FollowButton'

const hanako = { id: 'u2', handle: 'hanako', following: false }

describe('FollowButton', () => {
  it('自分自身には出さない', () => {
    renderWithProviders(<FollowButton user={{ id: user.id, handle: user.handle, following: false }} />, { me: user })

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('フォローしていなければ「フォロー」、していれば「フォロー中」を出す', () => {
    const { unmount } = renderWithProviders(<FollowButton user={hanako} />, { me: user })
    expect(screen.getByRole('button', { name: '@hanako をフォロー' })).toHaveAttribute('aria-pressed', 'false')
    unmount()

    renderWithProviders(<FollowButton user={{ ...hanako, following: true }} />, { me: user })
    const button = screen.getByRole('button', { name: '@hanako のフォローを解除' })
    expect(button).toHaveAttribute('aria-pressed', 'true')
    expect(button).toHaveTextContent('フォロー中')
  })

  it('フォローを送り、プロフィール・一覧・タイムラインを取り直す', async () => {
    const fetchMock = mockApi({ '/api/users/hanako/follow': () => json(200, { following: true, followerCount: 1 }) })
    const { queryClient } = renderWithProviders(<FollowButton user={hanako} />, { me: user })
    queryClient.setQueryData(userKeys.profile('hanako', user.id), {})
    queryClient.setQueryData(timelineKeys.list('home', user.id), { pages: [], pageParams: [] })

    await userEvent.click(screen.getByRole('button', { name: '@hanako をフォロー' }))

    expect(request(fetchMock)).toMatchObject({ path: '/api/users/hanako/follow', method: 'PUT' })
    await expect.poll(() => queryClient.getQueryState(userKeys.profile('hanako', user.id))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(timelineKeys.list('home', user.id))?.isInvalidated).toBe(true)
  })

  it('フォロー中なら解除を送る', async () => {
    const fetchMock = mockApi({ '/api/users/hanako/follow': () => json(200, { following: false, followerCount: 0 }) })
    renderWithProviders(<FollowButton user={{ ...hanako, following: true }} />, { me: user })

    await userEvent.click(screen.getByRole('button', { name: '@hanako のフォローを解除' }))

    expect(request(fetchMock).method).toBe('DELETE')
  })

  it('送信中は押せない', async () => {
    const response = deferred<Response>()
    const fetchMock = mockApi({ '/api/users/hanako/follow': () => response.promise })
    renderWithProviders(<FollowButton user={hanako} />, { me: user })

    await userEvent.click(screen.getByRole('button', { name: '@hanako をフォロー' }))

    expect(screen.getByRole('button', { name: '@hanako をフォロー' })).toBeDisabled()
    expect(fetchMock).toHaveBeenCalledOnce()
    response.resolve(json(200, { following: true, followerCount: 1 }))
    await expect.poll(() => screen.getByRole('button', { name: '@hanako をフォロー' })).toBeEnabled()
  })

  it('失敗したら理由をトーストで出す', async () => {
    mockApi({ '/api/users/hanako/follow': () => json(403, { code: 'BLOCKED', detail: 'このユーザーはフォローできません' }) })
    renderWithProviders(<FollowButton user={hanako} />, { me: user })

    await userEvent.click(screen.getByRole('button', { name: '@hanako をフォロー' }))

    expect(await screen.findByRole('status')).toHaveTextContent('このユーザーはフォローできません')
  })

  it('未ログインならログイン画面へ誘導する', async () => {
    const fetchMock = mockApi({})
    renderWithProviders(<FollowButton user={hanako} />, { path: '/users/hanako' })

    await userEvent.click(screen.getByRole('button', { name: '@hanako をフォロー' }))

    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })

  it('押しても親（一覧の行）のクリックにはならない', async () => {
    mockApi({ '/api/users/hanako/follow': () => json(200, { following: true, followerCount: 1 }) })
    const onRowClick = vi.fn()
    renderWithProviders(
      <div onClick={onRowClick}>
        <FollowButton user={hanako} />
      </div>,
      { me: user },
    )

    await userEvent.click(screen.getByRole('button', { name: '@hanako をフォロー' }))

    expect(onRowClick).not.toHaveBeenCalled()
  })
})
