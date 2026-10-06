import { useNavigate } from 'react-router'
import type { UserSummary } from '../api/types'
import { useAuth } from '../auth/context'
import { Avatar } from './Avatar'
import { FollowButton } from './FollowButton'

// ユーザー一覧（フォロー・フォロワー一覧など）の 1 件
export function UserListItem({ user }: { user: UserSummary }) {
  const { me } = useAuth()
  const navigate = useNavigate()
  const followsMe = !!me && me.id !== user.id && user.followedBy

  return (
    <div
      className="flex cursor-pointer gap-3 border-b border-slate-100 px-4 py-3 hover:bg-slate-50/70"
      onClick={() => navigate(`/users/${user.handle}`)}
    >
      <Avatar user={user} />
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="truncate font-bold hover:underline">{user.displayName}</p>
            <p className="flex items-center gap-1.5 text-sm text-slate-500">
              @{user.handle}
              {followsMe && <span className="rounded bg-slate-100 px-1 text-xs">フォローされています</span>}
            </p>
          </div>
          <FollowButton user={user} />
        </div>
        {user.bio && <p className="mt-1 text-sm break-words whitespace-pre-wrap">{user.bio}</p>}
      </div>
    </div>
  )
}
