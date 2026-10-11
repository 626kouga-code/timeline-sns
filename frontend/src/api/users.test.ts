import { describe, expect, it } from 'vitest'
import { json, mockApi, page, request, user } from '../test/utils'
import type { Profile } from './types'
import {
  deleteAvatar,
  fetchFollowList,
  fetchUserPosts,
  follow,
  getProfile,
  searchUsers,
  unfollow,
  updateMe,
  uploadAvatar,
  userKeys,
} from './users'

const profile: Profile = {
  id: 'u2',
  handle: 'hanako',
  displayName: '花子',
  bio: '',
  avatarUrl: null,
  createdAt: '2026-10-01T00:00:00Z',
  postCount: 1,
  followingCount: 2,
  followerCount: 3,
  following: false,
  followedBy: false,
  blocking: false,
  blockedBy: false,
}

describe('ユーザーの API', () => {
  it('プロフィールを取る', async () => {
    const fetchMock = mockApi({ '/api/users/hanako': () => json(200, profile) })

    await expect(getProfile('hanako')).resolves.toEqual(profile)
    expect(request(fetchMock).method).toBe('GET')
  })

  it('凍結・存在しないユーザーは 404 のエラーになる', async () => {
    mockApi({
      '/api/users/nobody': () =>
        json(404, { code: 'USER_NOT_FOUND', detail: 'このユーザーは存在しないか、表示できません' }),
    })

    await expect(getProfile('nobody')).rejects.toMatchObject({ status: 404, code: 'USER_NOT_FOUND' })
  })

  it('投稿一覧・フォロー一覧は cursor を付けて取る', async () => {
    const fetchMock = mockApi({
      '/api/users/hanako/posts?cursor=p1': () => page([]),
      '/api/users/hanako/following': () => page([]),
      '/api/users/hanako/followers?cursor=u9': () => page([]),
    })

    await fetchUserPosts('hanako', 'p1')
    await fetchFollowList('hanako', 'following', null)
    await fetchFollowList('hanako', 'followers', 'u9')

    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/users/hanako/posts?cursor=p1',
      '/api/users/hanako/following',
      '/api/users/hanako/followers?cursor=u9',
    ])
  })

  it('検索語は URL エンコードして送る', async () => {
    const fetchMock = mockApi({ '/api/search/users': () => page([]) })

    await searchUsers('太郎 & #1', null)
    await searchUsers('taro', 'u5')

    const first = new URL(request(fetchMock, 0).path, 'http://localhost')
    expect(first.searchParams.get('q')).toBe('太郎 & #1')
    expect(first.searchParams.has('cursor')).toBe(false)
    expect(request(fetchMock, 1).path).toBe('/api/search/users?q=taro&cursor=u5')
  })

  it('フォローは PUT、解除は DELETE を送る', async () => {
    const fetchMock = mockApi({
      '/api/users/hanako/follow': (_path, init) =>
        json(
          200,
          init?.method === 'PUT' ? { following: true, followerCount: 4 } : { following: false, followerCount: 3 },
        ),
    })

    await expect(follow('hanako')).resolves.toEqual({ following: true, followerCount: 4 })
    await expect(unfollow('hanako')).resolves.toEqual({ following: false, followerCount: 3 })
    expect(fetchMock.mock.calls.map(([, init]) => init?.method)).toEqual(['PUT', 'DELETE'])
  })

  it('自分自身のフォローは 400 のエラーになる', async () => {
    mockApi({
      '/api/users/taro/follow': () => json(400, { code: 'CANNOT_FOLLOW_SELF', detail: '自分自身はフォローできません' }),
    })

    await expect(follow('taro')).rejects.toMatchObject({ code: 'CANNOT_FOLLOW_SELF' })
  })

  it('プロフィール編集は表示名と自己紹介を PATCH で送る', async () => {
    const fetchMock = mockApi({ '/api/me': () => json(200, { ...user, displayName: '新しい名前', bio: 'よろしく' }) })

    await expect(updateMe({ displayName: '新しい名前', bio: 'よろしく' })).resolves.toMatchObject({
      displayName: '新しい名前',
    })

    expect(request(fetchMock).method).toBe('PATCH')
    expect(JSON.parse(request(fetchMock).body as string)).toEqual({ displayName: '新しい名前', bio: 'よろしく' })
  })

  it('アイコン画像は file パートで送り、削除は DELETE を送る', async () => {
    const fetchMock = mockApi({ '/api/me/avatar': () => json(200, { ...user, avatarUrl: '/api/media/a.jpg' }) })

    await expect(uploadAvatar(new Blob(['x'], { type: 'image/png' }))).resolves.toMatchObject({
      avatarUrl: '/api/media/a.jpg',
    })
    await deleteAvatar()

    const upload = request(fetchMock, 0)
    expect(upload.method).toBe('PUT')
    expect((upload.body as FormData).get('file')).toBeInstanceOf(Blob)
    expect(request(fetchMock, 1).method).toBe('DELETE')
  })

  it('キャッシュのキーはハンドルの大文字小文字を区別しない', () => {
    expect(userKeys.profile('Hanako', 'u1')).toEqual(userKeys.profile('hanako', 'u1'))
    expect(userKeys.followList('HANAKO', 'followers', 'u1')).toEqual(userKeys.followList('hanako', 'followers', 'u1'))
    // 検索のキーはプロフィールのキーと重ならない
    expect(userKeys.search('hanako', 'u1')[1]).toBe('#search')
  })
})
