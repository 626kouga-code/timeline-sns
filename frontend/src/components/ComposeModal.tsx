import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client'
import { createPost } from '../api/posts'
import { timelineKeys } from '../api/timeline'
import { useAuth } from '../auth/context'
import { Avatar } from './Avatar'
import { Icon } from './Icon'
import { IMAGE_TYPES, imageFileError } from './imageFiles'
import { Modal } from './Modal'
import { countChars, MAX_BODY } from './text'
import { useToast } from './toast'

const MAX_IMAGES = 4

interface Selected {
  file: File
  /** プレビュー用の URL（URL.createObjectURL）。取り除いたとき・閉じたときに解放する */
  preview: string
}

// 本文 1〜280 文字、画像は最大 4 枚。画像があれば本文なしでも投稿できる（F-10・F-11）
export function ComposeModal({ onClose }: { onClose: () => void }) {
  const { me } = useAuth()
  const showToast = useToast()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [images, setImages] = useState<Selected[]>([])
  const [imageError, setImageError] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const mutation = useMutation({
    mutationFn: createPost,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: timelineKeys.all })
      showToast('投稿しました')
      onClose()
    },
  })

  // 閉じたら、残っているプレビューの URL を解放する
  const previews = useRef<string[]>([])
  useEffect(() => {
    previews.current = images.map((image) => image.preview)
  }, [images])
  useEffect(() => () => previews.current.forEach((url) => URL.revokeObjectURL(url)), [])

  if (!me) return null

  const length = countChars(body)
  const over = length > MAX_BODY
  const canSubmit = !over && (body.trim().length > 0 || images.length > 0) && !mutation.isPending
  const serverError =
    mutation.error instanceof ApiError
      ? (mutation.error.errors.body ?? mutation.error.errors.images ?? mutation.error.message)
      : null

  const addFiles = (files: File[]) => {
    setImageError(null)
    const added: Selected[] = []
    for (const file of files) {
      if (images.length + added.length >= MAX_IMAGES) {
        setImageError(`画像は ${MAX_IMAGES} 枚までです`)
        break
      }
      const error = imageFileError(file)
      if (error) {
        setImageError(error)
        continue
      }
      added.push({ file, preview: URL.createObjectURL(file) })
    }
    setImages([...images, ...added])
  }

  const remove = (target: Selected) => {
    URL.revokeObjectURL(target.preview)
    setImages(images.filter((image) => image !== target))
    setImageError(null)
  }

  return (
    <Modal title="投稿する" onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (canSubmit) mutation.mutate({ body, images: images.map((image) => image.file) })
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
            {images.length > 0 && (
              <ul className="mt-2 grid grid-cols-2 gap-2" aria-label="添付する画像">
                {images.map((image, i) => (
                  <li key={image.preview} className="relative h-32 overflow-hidden rounded-xl bg-slate-100">
                    <img src={image.preview} alt={`添付する画像 ${i + 1}`} className="size-full object-cover" />
                    <button
                      type="button"
                      onClick={() => remove(image)}
                      disabled={mutation.isPending}
                      className="absolute top-1.5 right-1.5 rounded-full bg-black/60 p-1 text-white hover:bg-black/80"
                      aria-label={`画像 ${i + 1} を取り除く`}
                    >
                      <Icon name="x" className="size-4" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {(imageError ?? serverError) && (
              <p role="alert" className="mt-2 text-sm text-red-600">
                {imageError ?? serverError}
              </p>
            )}
          </div>
        </div>
        <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3">
          <div>
            <input
              ref={fileInput}
              type="file"
              accept={IMAGE_TYPES.join(',')}
              multiple
              hidden
              data-testid="compose-images"
              onChange={(e) => {
                addFiles(Array.from(e.target.files ?? []))
                // 同じファイルを選び直しても onChange が呼ばれるよう、選択を空に戻す
                e.target.value = ''
              }}
            />
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              disabled={images.length >= MAX_IMAGES || mutation.isPending}
              className="rounded-full p-2 text-sky-600 hover:bg-sky-50 disabled:opacity-40"
              aria-label="画像を追加"
              title={`画像を追加（最大 ${MAX_IMAGES} 枚）`}
            >
              <Icon name="image" />
            </button>
          </div>
          <div className="flex items-center gap-3">
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
              {mutation.isPending ? '投稿中…' : '投稿する'}
            </button>
          </div>
        </div>
      </form>
    </Modal>
  )
}
