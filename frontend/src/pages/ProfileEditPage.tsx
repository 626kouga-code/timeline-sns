import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { timelineKeys } from '../api/timeline'
import type { Me } from '../api/types'
import { deleteAvatar, updateMe, uploadAvatar, userKeys, type ProfileInput } from '../api/users'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { AvatarCropper } from '../components/AvatarCropper'
import { Icon } from '../components/Icon'
import { IMAGE_TYPES, imageFileError } from '../components/imageFiles'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../components/toast'

const MAX_NAME = 50
const MAX_BIO = 160

// プロフィール編集（F-41, `/settings/profile`）
export function ProfileEditPage() {
  const { me, setMe } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const showToast = useToast()
  const [displayName, setDisplayName] = useState(me?.displayName ?? '')
  const [bio, setBio] = useState(me?.bio ?? '')

  const fileInput = useRef<HTMLInputElement>(null)
  const [cropping, setCropping] = useState<File | null>(null)

  // 表示名・アイコンは投稿・コメント・一覧にも出ているので、まとめて取り直す
  const refreshEverywhere = (updated: Me) => {
    setMe(updated)
    void queryClient.invalidateQueries({ queryKey: userKeys.all })
    void queryClient.invalidateQueries({ queryKey: timelineKeys.all })
    void queryClient.invalidateQueries({ queryKey: ['post'] })
    void queryClient.invalidateQueries({ queryKey: ['comments'] })
  }

  const mutation = useMutation({
    mutationFn: (input: ProfileInput) => updateMe(input),
    onSuccess: (updated) => {
      refreshEverywhere(updated)
      showToast('プロフィールを更新しました')
      navigate(`/users/${updated.handle}`)
    },
  })

  // アイコンはその場で保存する（表示名・自己紹介の「保存する」とは別）。失敗したら切り抜き画面にメッセージを出す
  const saveAvatar = (updated: Me, message: string) => {
    refreshEverywhere(updated)
    showToast(message)
  }
  const removeAvatar = useMutation({
    mutationFn: deleteAvatar,
    onSuccess: (updated) => saveAvatar(updated, 'アイコン画像を削除しました'),
    onError: (error) => showToast(error instanceof ApiError ? error.message : 'アイコン画像を削除できませんでした'),
  })
  const avatarBusy = cropping !== null || removeAvatar.isPending

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
          <button
            type="button"
            onClick={() => fileInput.current?.click()}
            disabled={avatarBusy}
            className="group relative rounded-full"
            aria-label="アイコン画像を変更"
          >
            <Avatar user={{ ...me, displayName: displayName.trim() || me.displayName }} size="lg" />
            <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/40 text-white opacity-0 transition group-hover:opacity-100">
              <Icon name="image" className="size-7" />
            </span>
          </button>
          <div className="text-sm text-slate-500">
            <p>アイコンをクリックして画像を選択（JPEG・PNG・WebP・GIF、5MB まで）</p>
            {me.avatarUrl && (
              <button
                type="button"
                onClick={() => removeAvatar.mutate()}
                disabled={avatarBusy}
                className="mt-1 text-red-600 hover:underline disabled:opacity-40"
              >
                画像を削除
              </button>
            )}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept={IMAGE_TYPES.join(',')}
            hidden
            data-testid="avatar-input"
            onChange={(e) => {
              const file = e.target.files?.[0]
              // 同じファイルを選び直しても onChange が呼ばれるよう、選択を空に戻す
              e.target.value = ''
              if (!file) return
              const error = imageFileError(file)
              if (error) showToast(error)
              else setCropping(file)
            }}
          />
        </div>

        {cropping && (
          <AvatarCropper
            file={cropping}
            onClose={() => setCropping(null)}
            onApply={async (image) => {
              saveAvatar(await uploadAvatar(image), 'アイコン画像を変更しました')
              setCropping(null)
            }}
          />
        )}

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
