import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useStore } from '../mock/store'
import type { PostImage } from '../mock/types'
import { Avatar } from './Avatar'
import { Icon } from './Icon'
import { Modal } from './Modal'
import { countChars, MAX_BODY } from './text'

const MAX_IMAGES = 4
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const ACCEPT = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

// 本文 1〜280 文字、画像は最大 4 枚。画像があれば本文なしでも投稿できる（F-10, F-11）
export function ComposeModal({ onClose }: { onClose: () => void }) {
  const { me, createPost, showToast } = useStore()
  const navigate = useNavigate()
  const [body, setBody] = useState('')
  const [images, setImages] = useState<PostImage[]>([])
  const [error, setError] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  if (!me) return null

  const length = countChars(body)
  const over = length > MAX_BODY
  const canSubmit = !over && (body.trim().length > 0 || images.length > 0)

  const addFiles = (files: FileList | null) => {
    if (!files) return
    setError(null)
    const next = [...images]
    for (const file of Array.from(files)) {
      if (next.length >= MAX_IMAGES) {
        setError(`画像は ${MAX_IMAGES} 枚までです`)
        break
      }
      if (!ACCEPT.includes(file.type)) {
        setError('JPEG / PNG / WebP / GIF のみ投稿できます')
        continue
      }
      if (file.size > MAX_IMAGE_BYTES) {
        setError('画像は 1 枚 5MB までです')
        continue
      }
      // 本来はサーバーで Exif 除去・サムネイル生成を行う。プロトタイプではブラウザ上のプレビューのみ
      next.push({ id: crypto.randomUUID(), url: URL.createObjectURL(file), width: 0, height: 0 })
    }
    setImages(next)
  }

  return (
    <Modal title="投稿する" onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (!canSubmit) return
          const id = createPost(body.trim(), images)
          showToast('投稿しました')
          onClose()
          navigate(`/posts/${id}`)
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
              rows={4}
              className="w-full resize-none text-lg placeholder:text-slate-400 focus:outline-none"
            />
            {images.length > 0 && (
              <div className="mt-2 grid grid-cols-2 gap-2">
                {images.map((img) => (
                  <div key={img.id} className="relative h-32 overflow-hidden rounded-xl">
                    <img src={img.url} alt="" className="size-full object-cover" />
                    <button
                      type="button"
                      onClick={() => setImages((prev) => prev.filter((i) => i.id !== img.id))}
                      className="absolute top-1.5 right-1.5 rounded-full bg-black/60 p-1 text-white hover:bg-black/80"
                      aria-label="画像を取り除く"
                    >
                      <Icon name="x" className="size-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
            {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
          </div>
        </div>
        <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3">
          <div>
            <input
              ref={fileInput}
              type="file"
              accept={ACCEPT.join(',')}
              multiple
              hidden
              onChange={(e) => {
                addFiles(e.target.files)
                e.target.value = ''
              }}
            />
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              disabled={images.length >= MAX_IMAGES}
              className="rounded-full p-2 text-sky-600 hover:bg-sky-50 disabled:opacity-40"
              aria-label="画像を追加"
              title={`画像を追加（最大 ${MAX_IMAGES} 枚）`}
            >
              <Icon name="image" />
            </button>
          </div>
          <div className="flex items-center gap-3">
            <span className={`text-sm tabular-nums ${over ? 'font-bold text-red-600' : length > MAX_BODY - 20 ? 'text-amber-600' : 'text-slate-400'}`}>
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
        </div>
      </form>
    </Modal>
  )
}
