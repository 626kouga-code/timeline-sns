// バックエンドの応答型（backend/src/main/java/com/timelinesns/**/ *Response.java と同じ項目）

export type Role = 'USER' | 'ADMIN'

/** ログイン中の本人の情報（MeResponse） */
export interface Me {
  id: string
  email: string
  handle: string
  displayName: string
  bio: string | null
  /** アイコン画像の URL。未設定なら null */
  avatarUrl: string | null
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
  /** アイコン画像の URL。未設定なら null */
  avatarUrl: string | null
}

/** 投稿の画像（PostResponse.Image） */
export interface PostImage {
  /** 拡大表示用（長辺 2048px まで。GIF はアニメーションのまま） */
  url: string
  /** 一覧用（長辺 640px まで） */
  thumbnailUrl: string
  width: number
  height: number
}

/** 投稿（PostResponse） */
export interface Post {
  id: string
  /** 画像だけの投稿なら空文字 */
  body: string
  /** 0〜4 枚、投稿したときの順 */
  images: PostImage[]
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

/** コメント（CommentResponse）。スレッドの組み立てはフロントで行う */
export interface Comment {
  id: string
  /** 返信先のコメント。投稿への直接のコメントなら null */
  parentId: string | null
  /** 削除済みなら null */
  body: string | null
  /** ISO 8601 */
  createdAt: string
  /** 削除済み（退会したユーザーのものを含む）なら null */
  author: PostAuthor | null
  /** 返信が付いているので「このコメントは削除されました」として残っている */
  deleted: boolean
}

/** いいね・取り消しの応答（LikeResponse） */
export interface LikeState {
  liked: boolean
  likeCount: number
}

/** プロフィール（ProfileResponse）。関係を表す項目はゲストなら常に false */
export interface Profile {
  id: string
  handle: string
  displayName: string
  bio: string
  avatarUrl: string | null
  /** ISO 8601 */
  createdAt: string
  postCount: number
  /** 凍結されたユーザーは数えない（フォロワー数も同じ） */
  followingCount: number
  followerCount: number
  /** 閲覧者がこのユーザーをフォローしている */
  following: boolean
  /** このユーザーが閲覧者をフォローしている */
  followedBy: boolean
  /** 閲覧者がこのユーザーをブロックしている */
  blocking: boolean
  /** このユーザーが閲覧者をブロックしている */
  blockedBy: boolean
}

/** ユーザー一覧の 1 件（UserSummaryResponse） */
export interface UserSummary {
  id: string
  handle: string
  displayName: string
  bio: string
  avatarUrl: string | null
  following: boolean
  followedBy: boolean
}

/** フォロー・解除の応答（FollowResponse） */
export interface FollowState {
  following: boolean
  followerCount: number
}

export type NotificationType = 'LIKE' | 'COMMENT' | 'REPLY' | 'FOLLOW'

/** 通知（NotificationResponse） */
export interface Notification {
  id: string
  type: NotificationType
  /** ISO 8601 */
  createdAt: string
  /** 取得した時点で未読だったか */
  unread: boolean
  /** 通知のもとになった操作をした人 */
  actor: PostAuthor
  /** 対象の投稿。FOLLOW なら null */
  post: { id: string; body: string; thumbnailUrl: string | null } | null
  /** コメント・返信。LIKE・FOLLOW なら null。body は削除済みなら null */
  comment: { id: string; body: string | null } | null
}
