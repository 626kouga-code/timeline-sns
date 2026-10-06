import { apiFetch } from './client'
import type { FollowState, Me, Page, Post, Profile, UserSummary } from './types'

/** following = フォロー中、followers = フォロワー（F-43） */
export type FollowListKind = 'following' | 'followers'

/**
 * TanStack Query のキー。フォロー・解除やプロフィール編集のあとは `userKeys.all` でまとめて取り直す。
 * ハンドルは大文字小文字を区別しないので小文字に揃える。関係の項目は閲覧者で変わるので、キーに閲覧者を含める。
 * プロフィールの投稿一覧はタイムラインと同じように扱いたい（いいねの反映・投稿後の取り直し）ので、
 * `timelineKeys.user` の方に置いている。
 */
export const userKeys = {
  all: ['user'] as const,
  profile: (handle: string, viewer: string) => ['user', handle.toLowerCase(), viewer] as const,
  followList: (handle: string, kind: FollowListKind, viewer: string) =>
    ['user', handle.toLowerCase(), viewer, kind] as const,
}

const base = (handle: string) => `/api/users/${encodeURIComponent(handle)}`
const cursorQuery = (cursor: string | null) => (cursor ? `?cursor=${encodeURIComponent(cursor)}` : '')

/** プロフィール（F-40） */
export function getProfile(handle: string): Promise<Profile> {
  return apiFetch<Profile>(base(handle))
}

/** プロフィールの投稿一覧（F-40）。cursor には前のページの nextCursor を渡す */
export function fetchUserPosts(handle: string, cursor: string | null): Promise<Page<Post>> {
  return apiFetch<Page<Post>>(`${base(handle)}/posts${cursorQuery(cursor)}`)
}

/** フォロー・フォロワー一覧（F-43）。フォローした日時の新しい順 */
export function fetchFollowList(handle: string, kind: FollowListKind, cursor: string | null): Promise<Page<UserSummary>> {
  return apiFetch<Page<UserSummary>>(`${base(handle)}/${kind}${cursorQuery(cursor)}`)
}

/** フォロー（F-42）。冪等なので二重に送っても状態は変わらない */
export function follow(handle: string): Promise<FollowState> {
  return apiFetch<FollowState>(`${base(handle)}/follow`, { method: 'PUT' })
}

/** フォロー解除（F-42） */
export function unfollow(handle: string): Promise<FollowState> {
  return apiFetch<FollowState>(`${base(handle)}/follow`, { method: 'DELETE' })
}

export interface ProfileInput {
  displayName: string
  bio: string
}

/** プロフィール編集（F-41）。表示名・自己紹介を変更し、変更後の本人の情報を返す */
export function updateMe(input: ProfileInput): Promise<Me> {
  return apiFetch<Me>('/api/me', { method: 'PATCH', body: input })
}

/** アイコン画像の変更（F-41）。画面で正方形に切り抜いた画像を送る。サーバーで 400×400 に作り直される */
export function uploadAvatar(image: Blob): Promise<Me> {
  const form = new FormData()
  form.append('file', image, 'avatar')
  return apiFetch<Me>('/api/me/avatar', { method: 'PUT', body: form })
}

/** アイコン画像の削除（F-41）。頭文字のアイコンに戻る */
export function deleteAvatar(): Promise<Me> {
  return apiFetch<Me>('/api/me/avatar', { method: 'DELETE' })
}
