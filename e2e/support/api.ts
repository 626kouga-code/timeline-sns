// テストの前提データを API で作るヘルパー（画面を通さずに、他のユーザーの操作などを用意する）。
// baseURL（5173）の /api プロキシ経由でバックエンドを呼ぶ。
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, type APIRequestContext, type APIResponse } from '@playwright/test'

/** テスト用の画像（240×160 の PNG）。投稿画像・アイコン画像に使う */
export const IMAGE_PATH = fileURLToPath(new URL('../fixtures/image.png', import.meta.url))
export const imageFile = () => ({ name: 'image.png', mimeType: 'image/png', buffer: readFileSync(IMAGE_PATH) })

export interface User {
  handle: string
  email: string
  password: string
  displayName: string
  token: string
}

export interface Post {
  id: string
  body: string
}

let seq = 0

/** ユーザー ID（^[A-Za-z0-9_]{3,15}$）を、ワーカー・実行をまたいでも重ならないように作る。 */
export function uniqueHandle(workerIndex: number): string {
  const time = Date.now().toString(36).slice(-6)
  return `e${workerIndex}${time}${(seq++).toString(36)}`.slice(0, 15)
}

async function ok(res: APIResponse, what: string): Promise<APIResponse> {
  expect(res.ok(), `${what} に失敗しました (${res.status()}): ${await res.text()}`).toBeTruthy()
  return res
}

const auth = (user: User) => ({ Authorization: `Bearer ${user.token}` })

export class Api {
  private readonly request: APIRequestContext
  private readonly workerIndex: number

  constructor(request: APIRequestContext, workerIndex: number) {
    this.request = request
    this.workerIndex = workerIndex
  }

  /** ユーザーを登録する。 */
  async createUser(displayName = 'E2E ユーザー'): Promise<User> {
    const handle = uniqueHandle(this.workerIndex)
    const user = { handle, email: `${handle}@example.com`, password: 'e2e-password', displayName }
    const res = await ok(
      await this.request.post('/api/auth/signup', { data: { ...user, agreedToTerms: true } }),
      'ユーザー登録',
    )
    return { ...user, token: (await res.json()).accessToken }
  }

  /** 投稿する（image を渡すと画像付き）。 */
  async createPost(user: User, body: string, image?: { name: string; mimeType: string; buffer: Buffer }): Promise<Post> {
    const res = await ok(
      await this.request.post('/api/posts', {
        headers: auth(user),
        multipart: image ? { body, images: image } : { body },
      }),
      '投稿',
    )
    return res.json()
  }

  async follow(user: User, target: User): Promise<void> {
    await ok(await this.request.put(`/api/users/${target.handle}/follow`, { headers: auth(user) }), 'フォロー')
  }

  async like(user: User, post: Post): Promise<void> {
    await ok(await this.request.post(`/api/posts/${post.id}/likes`, { headers: auth(user) }), 'いいね')
  }

  async comment(user: User, post: Post, body: string, parentId?: string): Promise<{ id: string }> {
    const res = await ok(
      await this.request.post(`/api/posts/${post.id}/comments`, { headers: auth(user), data: { body, parentId } }),
      'コメント',
    )
    return res.json()
  }
}
