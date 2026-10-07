import { useEffect, useState } from 'react'
import type { PostImage } from '../api/types'
import { Icon } from './Icon'

// 投稿の画像（F-11）。一覧ではサムネイルを並べ（はみ出す部分は切って表示）、タップで拡大表示する
export function ImageGrid({ images }: { images: PostImage[] }) {
  const [openIndex, setOpenIndex] = useState<number | null>(null)
  if (images.length === 0) return null

  const layout = images.length === 1 ? 'grid-cols-1' : 'grid-cols-2'
  const height = images.length === 1 ? 'h-72' : images.length === 2 ? 'h-56' : 'h-36'

  return (
    <>
      <div className={`mt-3 grid ${layout} gap-0.5 overflow-hidden rounded-2xl border border-slate-200`}>
        {images.map((image, i) => (
          <button
            key={image.url}
            type="button"
            className={`${height} ${images.length === 3 && i === 0 ? 'row-span-2 h-full' : ''} overflow-hidden bg-slate-100`}
            onClick={(e) => {
              e.stopPropagation()
              setOpenIndex(i)
            }}
            aria-label={`画像 ${i + 1} を拡大`}
          >
            <img
              src={image.thumbnailUrl}
              alt=""
              loading="lazy"
              className="size-full object-cover transition hover:opacity-90"
            />
          </button>
        ))}
      </div>
      {openIndex !== null && (
        <Lightbox images={images} index={openIndex} onChange={setOpenIndex} onClose={() => setOpenIndex(null)} />
      )}
    </>
  )
}

interface LightboxProps {
  images: PostImage[]
  index: number
  onChange: (index: number) => void
  onClose: () => void
}

// 拡大表示。表示用の画像（長辺 2048px まで。GIF はアニメーションのまま）を出す。← → で切り替え、Esc で閉じる
function Lightbox({ images, index, onChange, onClose }: LightboxProps) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      if (e.key === 'ArrowRight' && index < images.length - 1) onChange(index + 1)
      if (e.key === 'ArrowLeft' && index > 0) onChange(index - 1)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [images.length, index, onChange, onClose])

  const image = images[index]
  const nav = 'absolute top-1/2 -translate-y-1/2 rounded-full bg-black/50 px-3 py-2 text-2xl text-white hover:bg-black/70'
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/90"
      onClick={(e) => {
        e.stopPropagation()
        onClose()
      }}
      role="dialog"
      aria-modal="true"
      aria-label="画像の拡大表示"
    >
      <button type="button" className="absolute top-4 left-4 rounded-full bg-black/50 p-2 text-white" aria-label="閉じる">
        <Icon name="x" className="size-6" />
      </button>
      <img
        src={image.url}
        alt={`画像 ${index + 1} / ${images.length}`}
        width={image.width}
        height={image.height}
        className="h-auto max-h-[90vh] w-auto max-w-[90vw] object-contain"
        onClick={(e) => e.stopPropagation()}
      />
      {index > 0 && (
        <button
          type="button"
          className={`${nav} left-4`}
          onClick={(e) => {
            e.stopPropagation()
            onChange(index - 1)
          }}
          aria-label="前の画像"
        >
          ‹
        </button>
      )}
      {index < images.length - 1 && (
        <button
          type="button"
          className={`${nav} right-4`}
          onClick={(e) => {
            e.stopPropagation()
            onChange(index + 1)
          }}
          aria-label="次の画像"
        >
          ›
        </button>
      )}
    </div>
  )
}
