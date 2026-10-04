import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { createPost } from '../api/posts'
import { timelineKeys } from '../api/timeline'
import { useAuth } from '../auth/context'
import { Avatar } from './Avatar'
import { Modal } from './Modal'
import { countChars, MAX_BODY } from './text'
import { useToast } from './toast'

// 本文 1〜280 文字（F-10）。画像の追加（F-11）は #14 で行う
export function ComposeModal({ onClose }: { onClose: () => void }) {
  const { me } = useAuth()
  const showToast = useToast()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const mutation = useMutation({
    mutationFn: createPost,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: timelineKeys.all })
      showToast('投稿しました')
      onClose()
    },
  })

  if (!me) return null

  const length = countChars(body)
  const over = length > MAX_BODY
  const canSubmit = !over && body.trim().length > 0 && !mutation.isPending
  const error = mutation.error instanceof ApiError ? (mutation.error.errors.body ?? mutation.error.message) : null

  return (
    <Modal title="投稿する" onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (canSubmit) mutation.mutate(body)
        }}
      >
        <div className="flex gap-3">
          <Avatar user={me} />
          <div className="min-w-0 flex-1">
            <textarea
              autoFocus
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="いまどうしてる？"
              aria-label="本文"
              rows={4}
              className="w-full resize-none text-lg placeholder:text-slate-400 focus:outline-none"
            />
            {error && (
              <p role="alert" className="mt-2 text-sm text-red-600">
                {error}
              </p>
            )}
          </div>
        </div>
        <div className="mt-3 flex items-center justify-end gap-3 border-t border-slate-100 pt-3">
          <span
            className={`text-sm tabular-nums ${over ? 'font-bold text-red-600' : length > MAX_BODY - 20 ? 'text-amber-600' : 'text-slate-400'}`}
            aria-label="残り文字数"
          >
            {MAX_BODY - length}
          </span>
          <button
            type="submit"
            disabled={!canSubmit}
            className="rounded-full bg-sky-500 px-5 py-2 font-bold text-white hover:bg-sky-600 disabled:opacity-40"
          >
            投稿する
          </button>
        </div>
      </form>
    </Modal>
  )
}
