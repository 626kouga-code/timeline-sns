import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { UserSummary } from '../api/types'
import { json, mockApi, page, renderApp, session } from '../test/utils'

function summary(id: string, handle: string, overrides: Partial<UserSummary> = {}): UserSummary {
  return {
    id,
    handle,
    displayName: handle.toUpperCase(),
    bio: '',
    avatarUrl: null,
    following: false,
    followedBy: false,
    ...overrides,
  }
}

const searchCalls = (fetchMock: ReturnType<typeof mockApi>) =>
  fetchMock.mock.calls.map(([path]) => String(path)).filter((path) => path.startsWith('/api/search/users'))

describe('ユーザー検索', () => {
  it('入力が止まってから検索し、URL にキーワードを入れる', async () => {
    const fetchMock = mockApi({
      '/api/search/users': () => page([summary('u2', 'hanako'), summary('u3', 'hanabi')]),
    })
    renderApp('/search')

    expect(await screen.findByText('ユーザーID（@handle）や表示名の一部を入力してください')).toBeInTheDocument()
    await userEvent.type(screen.getByRole('searchbox', { name: 'ユーザーID・表示名で検索' }), 'hana')

    expect(await screen.findByText('HANAKO')).toBeInTheDocument()
    expect(screen.getByText('HANABI')).toBeInTheDocument()
    // 1 文字ごとではなく、入力が止まってから 1 回だけ問い合わせる
    expect(searchCalls(fetchMock)).toEqual(['/api/search/users?q=hana'])
  })

  it('URL のキーワードで検索した状態から始められる', async () => {
    mockApi({ '/api/search/users': () => page([summary('u2', 'hanako')]) })
    renderApp('/search?q=hanako')

    expect(await screen.findByText('HANAKO')).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toHaveValue('hanako')
  })

  it('一致するユーザーがいなければその旨を出す', async () => {
    mockApi({ '/api/search/users': () => page([]) })
    renderApp('/search?q=zzz')

    expect(await screen.findByText('「zzz」に一致するユーザーはいません')).toBeInTheDocument()
  })

  it('次のページを読み込める', async () => {
    mockApi({
      '/api/search/users?q=user': () => page([summary('u2', 'user1')], 'u2'),
      '/api/search/users?q=user&cursor=u2': () => page([summary('u3', 'user2')]),
    })
    renderApp('/search?q=user')

    await screen.findByText('USER1')
    await userEvent.click(screen.getByRole('button', { name: 'さらに読み込む' }))

    expect(await screen.findByText('USER2')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'さらに読み込む' })).not.toBeInTheDocument()
  })

  it('検索結果からフォローできる', async () => {
    let following = false
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/search/users': () => page([summary('u2', 'hanako', { following })]),
      '/api/users/hanako/follow': () => {
        following = true
        return json(200, { following: true, followerCount: 1 })
      },
    })
    renderApp('/search?q=hanako')

    await userEvent.click(await screen.findByRole('button', { name: '@hanako をフォロー' }))

    expect(await screen.findByRole('button', { name: '@hanako のフォローを解除' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/users/hanako/follow', expect.objectContaining({ method: 'PUT' }))
  })
})
