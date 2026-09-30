import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { createInitialDb } from './data'
import { isBlockedBetween } from './selectors'
import type { Db, NotificationType, PostImage, ReportReason, ReportStatus, ReportTargetType, User } from './types'

// バックエンドの代わりにメモリ上で状態を持つ。リロードすると初期データに戻る。

type Result = { ok: true } | { ok: false; error: string }

export interface SignupInput {
  email: string
  password: string
  handle: string
  displayName: string
}

interface StoreValue {
  db: Db
  me: User | null
  toast: string | null
  showToast: (message: string) => void
  login: (email: string, password: string) => Result
  loginAs: (userId: string) => Result
  logout: () => void
  signup: (input: SignupInput) => Result
  createPost: (body: string, images: PostImage[]) => string
  deletePost: (postId: string) => void
  toggleLike: (postId: string) => void
  addComment: (postId: string, body: string, parentId: string | null) => void
  deleteComment: (commentId: string) => void
  follow: (userId: string) => void
  unfollow: (userId: string) => void
  block: (userId: string) => void
  unblock: (userId: string) => void
  report: (targetType: ReportTargetType, targetId: string, reason: ReportReason, detail: string) => void
  markNotificationsRead: () => void
  updateProfile: (patch: Pick<User, 'displayName' | 'bio' | 'avatarUrl'>) => void
  setReportStatus: (reportId: string, status: ReportStatus) => void
  adminDeletePost: (postId: string) => void
  adminDeleteComment: (commentId: string) => void
  setSuspended: (userId: string, suspended: boolean) => void
  reset: () => void
}

const StoreContext = createContext<StoreValue | null>(null)

let seq = 1000
const newId = (prefix: string) => `${prefix}${++seq}`

function withNotification(
  db: Db,
  recipientId: string,
  actorId: string,
  type: NotificationType,
  postId: string | null = null,
  commentId: string | null = null,
): Db {
  if (recipientId === actorId || isBlockedBetween(db, recipientId, actorId)) return db
  return {
    ...db,
    notifications: [
      { id: newId('n'), recipientId, actorId, type, postId, commentId, readAt: null, createdAt: Date.now() },
      ...db.notifications,
    ],
  }
}

function removePost(db: Db, postId: string): Db {
  return {
    ...db,
    posts: db.posts.filter((p) => p.id !== postId),
    comments: db.comments.filter((c) => c.postId !== postId),
    likes: db.likes.filter((l) => l.postId !== postId),
    notifications: db.notifications.filter((n) => n.postId !== postId),
  }
}

// 返信が付いていれば論理削除（F-33）、なければ物理削除する
function removeComment(db: Db, commentId: string): Db {
  const hasReplies = db.comments.some((c) => c.parentId === commentId)
  return {
    ...db,
    comments: hasReplies
      ? db.comments.map((c) => (c.id === commentId ? { ...c, deletedAt: Date.now() } : c))
      : db.comments.filter((c) => c.id !== commentId),
    notifications: db.notifications.filter((n) => n.commentId !== commentId),
  }
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [db, setDb] = useState<Db>(createInitialDb)
  const [meId, setMeId] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const toastTimer = useRef<number | undefined>(undefined)

  const me = meId ? (db.users.find((u) => u.id === meId) ?? null) : null

  const showToast = useCallback((message: string) => {
    setToast(message)
    window.clearTimeout(toastTimer.current)
    toastTimer.current = window.setTimeout(() => setToast(null), 2500)
  }, [])

  const value = useMemo<StoreValue>(() => {
    const requireMe = () => {
      if (!meId) throw new Error('ログインが必要です')
      return meId
    }

    const loginUser = (u: User | undefined): Result => {
      if (!u) return { ok: false, error: 'メールアドレスまたはパスワードが正しくありません' }
      if (u.status === 'SUSPENDED') return { ok: false, error: 'このアカウントは凍結されています' }
      setMeId(u.id)
      return { ok: true }
    }

    return {
      db,
      me,
      toast,
      showToast,
      login: (email, password) =>
        loginUser(db.users.find((u) => u.email === email.trim() && u.password === password)),
      loginAs: (userId) => loginUser(db.users.find((u) => u.id === userId)),
      logout: () => setMeId(null),
      signup: (input) => {
        if (db.users.some((u) => u.email === input.email)) {
          return { ok: false, error: 'このメールアドレスは既に登録されています' }
        }
        if (db.users.some((u) => u.handle.toLowerCase() === input.handle.toLowerCase())) {
          return { ok: false, error: 'このユーザーIDは既に使われています' }
        }
        const id = newId('u')
        const newUser: User = {
          id,
          ...input,
          bio: '',
          avatarUrl: null,
          avatarColor: '#14b8a6',
          role: 'USER',
          status: 'ACTIVE',
          createdAt: Date.now(),
        }
        setDb((d) => ({ ...d, users: [...d.users, newUser] }))
        setMeId(id)
        return { ok: true }
      },
      createPost: (body, images) => {
        const userId = requireMe()
        const id = newId('p')
        setDb((d) => ({ ...d, posts: [{ id, userId, body, images, createdAt: Date.now() }, ...d.posts] }))
        return id
      },
      deletePost: (postId) => setDb((d) => removePost(d, postId)),
      toggleLike: (postId) => {
        const userId = requireMe()
        setDb((d) => {
          const liked = d.likes.some((l) => l.userId === userId && l.postId === postId)
          if (liked) {
            return { ...d, likes: d.likes.filter((l) => !(l.userId === userId && l.postId === postId)) }
          }
          const post = d.posts.find((p) => p.id === postId)
          if (!post || isBlockedBetween(d, userId, post.userId)) return d
          const next = { ...d, likes: [...d.likes, { userId, postId, createdAt: Date.now() }] }
          return withNotification(next, post.userId, userId, 'LIKE', postId)
        })
      },
      addComment: (postId, body, parentId) => {
        const userId = requireMe()
        setDb((d) => {
          const post = d.posts.find((p) => p.id === postId)
          if (!post || isBlockedBetween(d, userId, post.userId)) return d
          const id = newId('c')
          let next: Db = {
            ...d,
            comments: [...d.comments, { id, postId, userId, parentId, body, deletedAt: null, createdAt: Date.now() }],
          }
          if (parentId) {
            const parent = d.comments.find((c) => c.id === parentId)
            if (parent && !parent.deletedAt) next = withNotification(next, parent.userId, userId, 'REPLY', postId, id)
          } else {
            next = withNotification(next, post.userId, userId, 'COMMENT', postId, id)
          }
          return next
        })
      },
      deleteComment: (commentId) => setDb((d) => removeComment(d, commentId)),
      follow: (targetId) => {
        const userId = requireMe()
        setDb((d) => {
          if (
            targetId === userId ||
            isBlockedBetween(d, userId, targetId) ||
            d.follows.some((f) => f.followerId === userId && f.followeeId === targetId)
          ) {
            return d
          }
          const next = { ...d, follows: [...d.follows, { followerId: userId, followeeId: targetId, createdAt: Date.now() }] }
          return withNotification(next, targetId, userId, 'FOLLOW')
        })
      },
      unfollow: (targetId) => {
        const userId = requireMe()
        setDb((d) => ({
          ...d,
          follows: d.follows.filter((f) => !(f.followerId === userId && f.followeeId === targetId)),
        }))
      },
      // ブロックすると既存のフォロー関係は双方向とも解除する（F-60）
      block: (targetId) => {
        const userId = requireMe()
        setDb((d) => ({
          ...d,
          blocks: [...d.blocks, { blockerId: userId, blockedId: targetId, createdAt: Date.now() }],
          follows: d.follows.filter(
            (f) =>
              !(f.followerId === userId && f.followeeId === targetId) &&
              !(f.followerId === targetId && f.followeeId === userId),
          ),
        }))
      },
      unblock: (targetId) => {
        const userId = requireMe()
        setDb((d) => ({
          ...d,
          blocks: d.blocks.filter((b) => !(b.blockerId === userId && b.blockedId === targetId)),
        }))
      },
      report: (targetType, targetId, reason, detail) => {
        const reporterId = requireMe()
        setDb((d) => ({
          ...d,
          reports: [
            { id: newId('r'), reporterId, targetType, targetId, reason, detail, status: 'OPEN', createdAt: Date.now() },
            ...d.reports,
          ],
        }))
      },
      markNotificationsRead: () => {
        const userId = requireMe()
        setDb((d) => {
          if (!d.notifications.some((n) => n.recipientId === userId && !n.readAt)) return d
          const readAt = Date.now()
          return {
            ...d,
            notifications: d.notifications.map((n) => (n.recipientId === userId && !n.readAt ? { ...n, readAt } : n)),
          }
        })
      },
      updateProfile: (patch) => {
        const userId = requireMe()
        setDb((d) => ({ ...d, users: d.users.map((u) => (u.id === userId ? { ...u, ...patch } : u)) }))
      },
      setReportStatus: (reportId, status) =>
        setDb((d) => ({ ...d, reports: d.reports.map((r) => (r.id === reportId ? { ...r, status } : r)) })),
      adminDeletePost: (postId) => setDb((d) => removePost(d, postId)),
      adminDeleteComment: (commentId) => setDb((d) => removeComment(d, commentId)),
      setSuspended: (userId, suspended) =>
        setDb((d) => ({
          ...d,
          users: d.users.map((u) => (u.id === userId ? { ...u, status: suspended ? 'SUSPENDED' : 'ACTIVE' } : u)),
        })),
      reset: () => {
        setDb(createInitialDb())
        setMeId(null)
      },
    }
  }, [db, me, meId, toast, showToast])

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useStore(): StoreValue {
  const value = useContext(StoreContext)
  if (!value) throw new Error('StoreProvider の外で useStore が呼ばれました')
  return value
}
