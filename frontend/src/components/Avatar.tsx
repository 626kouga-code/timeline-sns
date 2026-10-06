import { useState } from 'react'

const sizes = {
  sm: 'size-8 text-sm',
  md: 'size-10 text-base',
  lg: 'size-24 text-3xl border-4 border-white',
}

const colors = ['#0ea5e9', '#f97316', '#10b981', '#8b5cf6', '#ec4899', '#eab308', '#14b8a6', '#6366f1']

// アイコン画像がないユーザーは、ユーザーごとに決まった色の頭文字アイコンにする
function colorOf(id: string) {
  let hash = 0
  for (const ch of id) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return colors[Math.abs(hash) % colors.length]
}

interface AvatarUser {
  id: string
  displayName: string
  avatarUrl?: string | null
}

export function Avatar({ user, size = 'md' }: { user: AvatarUser; size?: keyof typeof sizes }) {
  // 読み込めなかった URL を覚えておき、頭文字のアイコンに切り替える（別の URL になれば再び試す）
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const url = user.avatarUrl && user.avatarUrl !== failedUrl ? user.avatarUrl : null

  if (url) {
    return (
      <img
        src={url}
        alt=""
        aria-hidden="true"
        onError={() => setFailedUrl(url)}
        className={`${sizes[size]} shrink-0 rounded-full bg-slate-100 object-cover`}
      />
    )
  }
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
