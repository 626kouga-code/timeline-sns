import type { Comment, Db, Post, User } from './types'

export const findUser = (db: Db, id: string): User | undefined => db.users.find((u) => u.id === id)

export const findUserByHandle = (db: Db, handle: string): User | undefined =>
  db.users.find((u) => u.handle.toLowerCase() === handle.toLowerCase())

export const isBlocking = (db: Db, blockerId: string, blockedId: string): boolean =>
  db.blocks.some((b) => b.blockerId === blockerId && b.blockedId === blockedId)

export const isBlockedBetween = (db: Db, a: string, b: string): boolean =>
  isBlocking(db, a, b) || isBlocking(db, b, a)

export const isFollowing = (db: Db, followerId: string, followeeId: string): boolean =>
  db.follows.some((f) => f.followerId === followerId && f.followeeId === followeeId)

// 凍結ユーザーとブロック関係にあるユーザーの投稿を除外する（F-60, F-63）
export function isPostVisible(db: Db, post: Post, viewerId: string | null): boolean {
  const author = findUser(db, post.userId)
  if (!author || author.status === 'SUSPENDED') return false
  return !viewerId || !isBlockedBetween(db, viewerId, post.userId)
}

export function isCommentVisible(db: Db, comment: Comment, viewerId: string | null): boolean {
  const author = findUser(db, comment.userId)
  if (!author || author.status === 'SUSPENDED') return false
  return !viewerId || !isBlockedBetween(db, viewerId, comment.userId)
}

const newestFirst = (a: Post, b: Post) => b.createdAt - a.createdAt

export const globalTimeline = (db: Db, viewerId: string | null): Post[] =>
  db.posts.filter((p) => isPostVisible(db, p, viewerId)).sort(newestFirst)

export function homeTimeline(db: Db, viewerId: string): Post[] {
  const ids = new Set([viewerId, ...db.follows.filter((f) => f.followerId === viewerId).map((f) => f.followeeId)])
  return db.posts.filter((p) => ids.has(p.userId) && isPostVisible(db, p, viewerId)).sort(newestFirst)
}

export const userPosts = (db: Db, userId: string, viewerId: string | null): Post[] =>
  db.posts.filter((p) => p.userId === userId && isPostVisible(db, p, viewerId)).sort(newestFirst)

export const likeCount = (db: Db, postId: string): number => db.likes.filter((l) => l.postId === postId).length

export const isLiked = (db: Db, postId: string, userId: string | null): boolean =>
  !!userId && db.likes.some((l) => l.postId === postId && l.userId === userId)

export const commentCount = (db: Db, postId: string): number =>
  db.comments.filter((c) => c.postId === postId && !c.deletedAt).length

export const followingOf = (db: Db, userId: string): User[] =>
  db.follows
    .filter((f) => f.followerId === userId)
    .map((f) => findUser(db, f.followeeId))
    .filter((u): u is User => !!u && u.status === 'ACTIVE')

export const followersOf = (db: Db, userId: string): User[] =>
  db.follows
    .filter((f) => f.followeeId === userId)
    .map((f) => findUser(db, f.followerId))
    .filter((u): u is User => !!u && u.status === 'ACTIVE')

export const unreadCount = (db: Db, userId: string): number =>
  db.notifications.filter((n) => n.recipientId === userId && !n.readAt).length

export function formatRelative(time: number): string {
  const diff = Date.now() - time
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return 'たった今'
  if (minutes < 60) return `${minutes}分`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}時間`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}日`
  const d = new Date(time)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

export function formatDateTime(time: number): string {
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
