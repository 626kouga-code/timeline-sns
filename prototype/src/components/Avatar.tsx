import type { User } from '../mock/types'

const sizes = {
  sm: 'size-8 text-sm',
  md: 'size-10 text-base',
  lg: 'size-24 text-3xl border-4 border-white',
}

export function Avatar({ user, size = 'md' }: { user: User; size?: keyof typeof sizes }) {
  if (user.avatarUrl) {
    return <img src={user.avatarUrl} alt="" className={`${sizes[size]} shrink-0 rounded-full object-cover`} />
  }
  return (
    <div
      className={`${sizes[size]} flex shrink-0 items-center justify-center rounded-full font-bold text-white`}
      style={{ backgroundColor: user.avatarColor }}
      aria-hidden="true"
    >
      {user.displayName.slice(0, 1)}
    </div>
  )
}
