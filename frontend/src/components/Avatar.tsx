const sizes = {
  sm: 'size-8 text-sm',
  md: 'size-10 text-base',
  lg: 'size-24 text-3xl border-4 border-white',
}

const colors = ['#0ea5e9', '#f97316', '#10b981', '#8b5cf6', '#ec4899', '#eab308', '#14b8a6', '#6366f1']

// 画像がまだ無いので、ユーザーごとに決まった色の頭文字アイコンにする（画像は #25 プロフィール編集で対応）
function colorOf(id: string) {
  let hash = 0
  for (const ch of id) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return colors[Math.abs(hash) % colors.length]
}

interface AvatarUser {
  id: string
  displayName: string
}

export function Avatar({ user, size = 'md' }: { user: AvatarUser; size?: keyof typeof sizes }) {
  return (
    <div
      className={`${sizes[size]} flex shrink-0 items-center justify-center rounded-full font-bold text-white`}
      style={{ backgroundColor: colorOf(user.id) }}
      aria-hidden="true"
    >
      {[...user.displayName][0]}
    </div>
  )
}
