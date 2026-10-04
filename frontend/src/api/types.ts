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
