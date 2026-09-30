import { isBlockedBetween, isFollowing } from '../mock/selectors'
import { useStore } from '../mock/store'
import type { User } from '../mock/types'
import { useRequireLogin } from './useRequireLogin'

export function FollowButton({ user }: { user: User }) {
  const { db, me, follow, unfollow } = useStore()
  const requireLogin = useRequireLogin()

  if (me?.id === user.id) return null
  if (me && isBlockedBetween(db, me.id, user.id)) return null

  const following = !!me && isFollowing(db, me.id, user.id)
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation()
        requireLogin(() => (following ? unfollow(user.id) : follow(user.id)))
      }}
      className={
        following
          ? 'group shrink-0 rounded-full border border-slate-300 px-4 py-1.5 text-sm font-bold hover:border-red-300 hover:bg-red-50 hover:text-red-600'
          : 'shrink-0 rounded-full bg-slate-900 px-4 py-1.5 text-sm font-bold text-white hover:bg-slate-700'
      }
    >
      {following ? (
        <>
          <span className="group-hover:hidden">フォロー中</span>
          <span className="hidden group-hover:inline">解除</span>
        </>
      ) : (
        'フォロー'
      )}
    </button>
  )
}
