import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, mockApi, renderWithProviders, user } from '../test/utils'
import { Layout, UNREAD_INTERVAL } from './Layout'
import { useLayout } from './layoutContext'

function Child() {
  const { openCompose } = useLayout()
  return (
    <button type="button" onClick={openCompose}>
      子の画面から投稿
    </button>
  )
}

function renderLayout(me: typeof user | null, path = '/') {
  return renderWithProviders(
    <Routes>
      <Route element={<Layout />}>
        <Route path="*" element={<Child />} />
      </Route>
    </Routes>,
    { me, path },
  )
}

// 左ナビ（PC）と下部ナビ（スマホ）の両方に同じリンクがあるので、左ナビの中で確かめる
const sideNav = () => screen.getAllByRole('navigation')[0]

afterEach(() => {
  vi.useRealTimers()
})

describe('Layout', () => {
  it('未ログインなら全体・検索・ログインだけを出し、投稿ボタンは出さない', () => {
    mockApi({})
    renderLayout(null)

    const links = within(sideNav()).getAllByRole('link')
    expect(links.map((link) => link.getAttribute('aria-label'))).toEqual(['全体', '検索', 'ログイン'])
    expect(screen.queryByRole('button', { name: '投稿する' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'ログアウト' })).not.toBeInTheDocument()
  })

  it('ログイン中は自分のプロフィールなどへのリンクを出す', async () => {
    mockApi({})
    renderLayout(user)

    const nav = sideNav()
    expect(within(nav).getAllByRole('link').map((link) => link.getAttribute('aria-label'))).toEqual([
      'ホーム',
      '全体',
      '検索',
      '通知',
      'プロフィール',
      '設定',
    ])
    expect(within(nav).getByRole('link', { name: 'プロフィール' })).toHaveAttribute('href', '/users/taro')
    expect(within(nav).queryByRole('link', { name: '管理' })).not.toBeInTheDocument()
  })

  it('管理者には管理画面へのリンクも出す', () => {
    mockApi({})
    renderLayout({ ...user, role: 'ADMIN' })

    expect(within(sideNav()).getByRole('link', { name: '管理' })).toHaveAttribute('href', '/admin/reports')
  })

  it('未読の通知数をバッジで出し、100 件以上は 99+ にする', async () => {
    mockApi({ '/api/notifications/unread-count': () => json(200, { count: 3 }) })
    const { unmount } = renderLayout(user)
    expect(await within(sideNav()).findByRole('link', { name: '通知（未読 3 件）' })).toHaveTextContent('3')
    unmount()

    mockApi({ '/api/notifications/unread-count': () => json(200, { count: 100 }) })
    renderLayout(user)
    expect(await within(sideNav()).findByRole('link', { name: '通知（未読 99+ 件）' })).toHaveTextContent('99+')
  })

  it('未読数は 30 秒ごとに確認し、未ログインなら確認しない', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let count = 0
    const fetchMock = mockApi({ '/api/notifications/unread-count': () => json(200, { count: ++count }) })
    renderLayout(user)
    expect(await within(sideNav()).findByRole('link', { name: '通知（未読 1 件）' })).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(UNREAD_INTERVAL))

    expect(await within(sideNav()).findByRole('link', { name: '通知（未読 2 件）' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('ゲストは未読数を問い合わせない', () => {
    const fetchMock = mockApi({})
    renderLayout(null)

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('投稿ボタン・子の画面から投稿画面を開ける', async () => {
    mockApi({})
    renderLayout(user)

    await userEvent.click(screen.getAllByRole('button', { name: '投稿する' })[0])
    expect(screen.getByRole('dialog', { name: '投稿する' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '子の画面から投稿' }))
    expect(screen.getByRole('dialog', { name: '投稿する' })).toBeInTheDocument()
  })

  it('ログアウトすると先にトップへ移ってからログアウトする', async () => {
    mockApi({})
    const { auth } = renderLayout(user, '/settings/profile')

    await userEvent.click(screen.getAllByRole('button', { name: 'ログアウト' })[0])

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
    expect(auth.logout).toHaveBeenCalledOnce()
  })

  it('ログアウトに失敗してもエラーにしない', async () => {
    mockApi({})
    const { auth } = renderLayout(user, '/home')
    vi.mocked(auth.logout).mockRejectedValue(new Error('network'))

    await userEvent.click(screen.getAllByRole('button', { name: 'ログアウト' })[0])

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})
