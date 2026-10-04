// バックエンドの応答型（backend/src/main/java/com/timelinesns/**/ *Response.java と同じ項目）

export type Role = 'USER' | 'ADMIN'

/** ログイン中の本人の情報（MeResponse） */
export interface Me {
  id: string
  email: string
  handle: string
  displayName: string
  bio: string | null
  role: Role
  emailVerified: boolean
}

/** 登録・ログイン・リフレッシュの応答（AuthResponse）。リフレッシュトークンは Cookie でだけ渡される */
export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: Me
}

/** 投稿者（PostResponse.Author）。本人以外にも返すので email は含まない */
export interface PostAuthor {
  id: string
  handle: string
  displayName: string
}

/** 投稿（PostResponse）。画像は #14 で追加する */
export interface Post {
  id: string
  body: string
  /** ISO 8601 */
  createdAt: string
  author: PostAuthor
  likeCount: number
  commentCount: number
  /** ログイン中のユーザーがいいねしているか（ゲストは常に false） */
  liked: boolean
}

/** カーソル方式の一覧の 1 ページ（PageResponse）。最後のページなら nextCursor は null */
export interface Page<T> {
  items: T[]
  nextCursor: string | null
}
