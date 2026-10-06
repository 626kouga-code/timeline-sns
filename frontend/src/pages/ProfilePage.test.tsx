import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Profile, UserSummary } from '../api/types'
import { json, mockApi, page, post, renderApp, session, user } from '../test/utils'

const hanako = { id: 'u2', handle: 'hanako', displayName: '花子' }

function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    ...hanako,
    bio: '花が好きです',
    createdAt: new Date().toISOString(),
    postCount: 1,
    followingCount: 3,
    followerCount: 5,
    following: false,
    followedBy: false,
    blocking: false,
    blockedBy: false,
    ...overrides,
  }
}

function summary(id: string, handle: string, overrides: Partial<UserSummary> = {}): UserSummary {
  return { id, handle, displayName: handle.toUpperCase(), bio: '', following: false, followedBy: false, ...overrides }
}

const notFound = () => json(404, { code: 'USER_NOT_FOUND', detail: 'このユーザーは存在しないか、表示できません' })

describe('プロフィール', () => {
  it('プロフィールと投稿一覧を表示する', async () => {
    mockApi({
      '/api/users/hanako': () => json(200, profile({ followedBy: true })),
      '/api/users/hanako/posts': () => page([post('p1', '花子の投稿', hanako)]),
    })
    renderApp('/users/hanako')

    expect(await screen.findByRole('heading', { name: '花子', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('花が好きです')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '3 フォロー' })).toHaveAttribute('href', '/users/hanako/following')
    expect(screen.getByRole('link', { name: '5 フォロワー' })).toHaveAttribute('href', '/users/hanako/followers')
    expect(await screen.findByText('花子の投稿')).toBeInTheDocument()
    // ゲストには「フォローされています」を出さない
    expect(screen.queryByText('フォローされています')).not.toBeInTheDocument()
  })

  it('存在しないユーザーは見つからない旨を出す', async () => {
    mockApi({ '/api/users/nobody': notFound })
    renderApp('/users/nobody')

    expect(await screen.findByText('@nobody は存在しないか、表示できません。')).toBeInTheDocument()
  })

  it('自分のプロフィールには編集ボタンを出し、フォローボタンは出さない', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/users/taro': () => json(200, profile({ id: user.id, handle: 'taro', displayName: '太郎' })),
      '/api/users/taro/posts': () => page([]),
    })
    renderApp('/users/taro')

    expect(await screen.findByRole('link', { name: 'プロフィールを編集' })).toHaveAttribute('href', '/settings/profile')
    expect(screen.queryByRole('button', { name: /フォロー/ })).not.toBeInTheDocument()
    expect(await screen.findByText('まだ投稿がありません')).toBeInTheDocument()
  })

  it('ブロック関係にあるときは投稿を問い合わせず、フォローボタンも出さない', async () => {
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/users/hanako': () => json(200, profile({ blockedBy: true })),
    })
    renderApp('/users/hanako')

    expect(await screen.findByText('投稿を表示できません')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /フォロー/ })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => String(path).includes('/posts'))).toBe(false)
  })
})

describe('フォロー', () => {
  it('押すとフォローし、取り直したプロフィールで表示が変わる', async () => {
    let following = false
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/users/hanako': () => json(200, profile({ following, followerCount: following ? 6 : 5 })),
      '/api/users/hanako/posts': () => page([]),
      '/api/users/hanako/follow': (_path, init) => {
        following = init?.method === 'PUT'
        return json(200, { following, followerCount: following ? 6 : 5 })
      },
    })
    renderApp('/users/hanako')

    await userEvent.click(await screen.findByRole('button', { name: '@hanako をフォロー' }))

    expect(await screen.findByRole('button', { name: '@hanako のフォローを解除' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '6 フォロワー' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/users/hanako/follow', expect.objectContaining({ method: 'PUT' }))

    await userEvent.click(screen.getByRole('button', { name: '@hanako のフォローを解除' }))
    expect(await screen.findByRole('button', { name: '@hanako をフォロー' })).toBeInTheDocument()
  })

  it('失敗したらメッセージを出す', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/users/hanako': () => json(200, profile()),
      '/api/users/hanako/posts': () => page([]),
      '/api/users/hanako/follow': () => json(403, { code: 'BLOCKED', detail: 'このユーザーはフォローできません' }),
    })
    renderApp('/users/hanako')

    await userEvent.click(await screen.findByRole('button', { name: '@hanako をフォロー' }))

    expect(await screen.findByText('このユーザーはフォローできません')).toBeInTheDocument()
  })

  it('未ログインで押すとログイン画面へ移る', async () => {
    const fetchMock = mockApi({
      '/api/users/hanako': () => json(200, profile()),
      '/api/users/hanako/posts': () => page([]),
    })
    renderApp('/users/hanako')

    await userEvent.click(await screen.findByRole('button', { name: '@hanako をフォロー' }))

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => String(path).endsWith('/follow'))).toBe(false)
  })
})

describe('フォロー・フォロワー一覧', () => {
  it('一覧を表示し、タブで切り替えられる', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/users/hanako': () => json(200, profile()),
      '/api/users/hanako/following': () =>
        page([summary('u3', 'jiro', { followedBy: true }), summary(user.id, 'taro')]),
      '/api/users/hanako/followers': () => page([]),
    })
    renderApp('/users/hanako/following')

    expect(await screen.findByText('JIRO')).toBeInTheDocument()
    expect(screen.getByText('フォローされています')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '@jiro をフォロー' })).toBeInTheDocument()
    // 自分自身にはフォローボタンを出さない
    expect(screen.queryByRole('button', { name: '@taro をフォロー' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'フォロワー' }))
    expect(await screen.findByText('まだフォロワーはいません')).toBeInTheDocument()
  })

  it('存在しないユーザーは見つからない旨を出す', async () => {
    mockApi({ '/api/users/nobody': notFound, '/api/users/nobody/followers': notFound })
    renderApp('/users/nobody/followers')

    expect(await screen.findByText('@nobody は存在しないか、表示できません。')).toBeInTheDocument()
  })
})

describe('プロフィール編集', () => {
  it('保存するとプロフィール画面へ移り、ナビゲーションの表示名も変わる', async () => {
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/me': (_path, init) => json(200, { ...user, ...(JSON.parse(String(init?.body)) as object) }),
      '/api/users/taro': () => json(200, profile({ id: user.id, handle: 'taro', displayName: '新しい名前' })),
      '/api/users/taro/posts': () => page([]),
    })
    renderApp('/settings/profile')

    const name = await screen.findByRole('textbox', { name: /表示名/ })
    await userEvent.clear(name)
    await userEvent.type(name, ' 新しい名前 ')
    await userEvent.type(screen.getByRole('textbox', { name: /自己紹介/ }), 'よろしく')
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('プロフィールを更新しました')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '新しい名前', level: 2 })).toBeInTheDocument()
    // ナビゲーションに出ていた元の表示名（太郎）は残らない
    expect(screen.queryByText('太郎')).not.toBeInTheDocument()
    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/me') ?? []
    expect(init).toMatchObject({ method: 'PATCH', body: JSON.stringify({ displayName: '新しい名前', bio: 'よろしく' }) })
  })

  it('文字数を超えると保存できない', async () => {
    mockApi({ '/api/auth/refresh': session })
    renderApp('/settings/profile')

    await userEvent.type(await screen.findByRole('textbox', { name: /自己紹介/ }), 'あ'.repeat(161))

    expect(screen.getByText('161/160')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled()
  })

  it('サーバーの入力エラーを項目の下に出す', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/me': () =>
        json(400, {
          code: 'VALIDATION_FAILED',
          detail: '入力内容に誤りがあります',
          errors: { displayName: '表示名を入力してください' },
        }),
    })
    renderApp('/settings/profile')

    await userEvent.click(await screen.findByRole('button', { name: '保存する' }))

    expect(await screen.findByText('表示名を入力してください')).toBeInTheDocument()
  })
})
