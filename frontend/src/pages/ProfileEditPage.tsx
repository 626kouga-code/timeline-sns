import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { timelineKeys } from '../api/timeline'
import { updateMe, userKeys, type ProfileInput } from '../api/users'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../components/toast'

const MAX_NAME = 50
const MAX_BIO = 160

// プロフィール編集（F-41, `/settings/profile`）。アイコン画像は画像の保存先（#40）が決まってから追加する
export function ProfileEditPage() {
  const { me, setMe } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const showToast = useToast()
  const [displayName, setDisplayName] = useState(me?.displayName ?? '')
  const [bio, setBio] = useState(me?.bio ?? '')

  const mutation = useMutation({
    mutationFn: (input: ProfileInput) => updateMe(input),
    onSuccess: (updated) => {
      setMe(updated)
      // 表示名は投稿・コメント・一覧にも出ているので、まとめて取り直す
      void queryClient.invalidateQueries({ queryKey: userKeys.all })
      void queryClient.invalidateQueries({ queryKey: timelineKeys.all })
      void queryClient.invalidateQueries({ queryKey: ['post'] })
      void queryClient.invalidateQueries({ queryKey: ['comments'] })
      showToast('プロフィールを更新しました')
      navigate(`/users/${updated.handle}`)
    },
  })

  if (!me) return null

  // 文字数はサーバーと同じくコードポイントで数える（絵文字も 1 文字）
  const nameLength = [...displayName.trim()].length
  const bioLength = [...bio.trim()].length
  const valid = nameLength > 0 && nameLength <= MAX_NAME && bioLength <= MAX_BIO
  const errors = mutation.error instanceof ApiError ? mutation.error.errors : {}

  return (
    <>
      <PageHeader title="プロフィールを編集" back />
      <form
        className="space-y-6 px-4 py-6"
        onSubmit={(e) => {
          e.preventDefault()
          if (!valid || mutation.isPending) return
          mutation.mutate({ displayName: displayName.trim(), bio: bio.trim() })
        }}
      >
        <div className="flex items-center gap-4">
          <Avatar user={{ id: me.id, displayName: displayName.trim() || me.displayName }} size="lg" />
          <p className="text-sm text-slate-500">アイコン画像の変更は準備中です。</p>
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
            aria-invalid={!!errors.displayName}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 focus:border-sky-500 focus:outline-none"
          />
          {errors.displayName && <span className="mt-1 block text-sm text-red-600">{errors.displayName}</span>}
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
            aria-invalid={!!errors.bio}
            className="mt-1 w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 focus:border-sky-500 focus:outline-none"
          />
          {errors.bio && <span className="mt-1 block text-sm text-red-600">{errors.bio}</span>}
        </label>

        {mutation.error && Object.keys(errors).length === 0 && (
          <p role="alert" className="text-sm text-red-600">
            {mutation.error.message}
          </p>
        )}

        <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">
          ユーザーID（@{me.handle}）とメールアドレスは変更できません。
        </div>

        <button
          type="submit"
          disabled={!valid || mutation.isPending}
          className="w-full rounded-full bg-slate-900 py-2.5 font-bold text-white hover:bg-slate-700 disabled:opacity-40"
        >
          {mutation.isPending ? '保存中…' : '保存する'}
        </button>
      </form>
    </>
  )
}
