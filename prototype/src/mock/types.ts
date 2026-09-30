// docs/05_data-model.md のテーブルに合わせたモック用の型。
// 本実装では API のレスポンス型になる想定だが、ここでは画面確認用に簡略化している。

export type Role = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'SUSPENDED'

export interface User {
  id: string
  email: string
  password: string
  handle: string
  displayName: string
  bio: string
  avatarUrl: string | null
  avatarColor: string
  role: Role
  status: UserStatus
  createdAt: number
}

export interface PostImage {
  id: string
  url: string
  width: number
  height: number
}

export interface Post {
  id: string
  userId: string
  body: string
  images: PostImage[]
  createdAt: number
}

export interface Comment {
  id: string
  postId: string
  userId: string
  parentId: string | null
  body: string
  deletedAt: number | null
  createdAt: number
}

export interface Like {
  userId: string
  postId: string
  createdAt: number
}

export interface Follow {
  followerId: string
  followeeId: string
  createdAt: number
}

export interface Block {
  blockerId: string
  blockedId: string
  createdAt: number
}

export type NotificationType = 'LIKE' | 'COMMENT' | 'REPLY' | 'FOLLOW'

export interface Notification {
  id: string
  recipientId: string
  actorId: string
  type: NotificationType
  postId: string | null
  commentId: string | null
  readAt: number | null
  createdAt: number
}

export type ReportTargetType = 'POST' | 'COMMENT' | 'USER'
export type ReportReason = 'SPAM' | 'HARASSMENT' | 'INAPPROPRIATE_IMAGE' | 'OTHER'
export type ReportStatus = 'OPEN' | 'RESOLVED' | 'REJECTED'

export interface Report {
  id: string
  reporterId: string
  targetType: ReportTargetType
  targetId: string
  reason: ReportReason
  detail: string
  status: ReportStatus
  createdAt: number
}

export interface Db {
  users: User[]
  posts: Post[]
  comments: Comment[]
  likes: Like[]
  follows: Follow[]
  blocks: Block[]
  notifications: Notification[]
  reports: Report[]
}

export const REPORT_REASON_LABELS: Record<ReportReason, string> = {
  SPAM: 'スパム',
  HARASSMENT: '嫌がらせ',
  INAPPROPRIATE_IMAGE: '不適切な画像',
  OTHER: 'その他',
}

export const REPORT_STATUS_LABELS: Record<ReportStatus, string> = {
  OPEN: '未対応',
  RESOLVED: '対応済み',
  REJECTED: '却下',
}
