import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Icon } from '../components/Icon'
import { PageHeader } from '../components/PageHeader'
import { useStore } from '../mock/store'

const MAX_NAME = 50
const MAX_BIO = 160

// プロフィール編集（F-41, `/settings/profile`）
export function ProfileEditPage() {
  const { me, updateProfile, showToast } = useStore()
  const navigate = useNavigate()
  const fileInput = useRef<HTMLInputElement>(null)
  const [displayName, setDisplayName] = useState(me?.displayName ?? '')
  const [bio, setBio] = useState(me?.bio ?? '')
  const [avatarUrl, setAvatarUrl] = useState(me?.avatarUrl ?? null)

  if (!me) return null

  const nameLength = [...displayName].length
  const bioLength = [...bio].length
  const valid = displayName.trim().length > 0 && nameLength <= MAX_NAME && bioLength <= MAX_BIO

  return (
    <>
      <PageHeader title="プロフィールを編集" back />
      <form
        className="space-y-6 px-4 py-6"
        onSubmit={(e) => {
          e.preventDefault()
          if (!valid) return
          updateProfile({ displayName: displayName.trim(), bio: bio.trim(), avatarUrl })
          showToast('プロフィールを更新しました')
          navigate(`/users/${me.handle}`)
        }}
      >
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={() => fileInput.current?.click()}
            className="group relative rounded-full"
            aria-label="アイコン画像を変更"
          >
            <Avatar user={{ ...me, displayName: displayName || me.displayName, avatarUrl }} size="lg" />
            <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/40 text-white opacity-0 transition group-hover:opacity-100">
              <Icon name="image" className="size-7" />
            </span>
          </button>
          <div className="text-sm text-slate-500">
            <p>アイコンをクリックして画像を選択</p>
            {avatarUrl && (
              <button type="button" onClick={() => setAvatarUrl(null)} className="mt-1 text-red-600 hover:underline">
                画像を削除
              </button>
            )}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            hidden
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) setAvatarUrl(URL.createObjectURL(file))
            }}
          />
        </div>

        <label className="block">
          <span className="flex justify-between text-sm font-semibold">
            表示名
            <span className={`font-normal tabular-nums ${nameLength > MAX_NAME ? 'text-red-600' : 'text-slate-400'}`}>
              {nameLength}/{MAX_NAME}
            </span>
          </span>
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 focus:border-sky-500 focus:outline-none"
          />
        </label>

        <label className="block">
          <span className="flex justify-between text-sm font-semibold">
            自己紹介
            <span className={`font-normal tabular-nums ${bioLength > MAX_BIO ? 'text-red-600' : 'text-slate-400'}`}>
              {bioLength}/{MAX_BIO}
            </span>
          </span>
          <textarea
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            rows={4}
            className="mt-1 w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 focus:border-sky-500 focus:outline-none"
          />
        </label>

        <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">
          ユーザーID（@{me.handle}）とメールアドレスは変更できません。パスワード変更・ブロック一覧・退会は
          アカウント設定（プロトタイプ対象外）で行います。
        </div>

        <button
          type="submit"
          disabled={!valid}
          className="w-full rounded-full bg-slate-900 py-2.5 font-bold text-white hover:bg-slate-700 disabled:opacity-40"
        >
          保存する
        </button>
      </form>
    </>
  )
}
