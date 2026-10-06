import { useEffect, useState } from 'react'
import Cropper, { type Area } from 'react-easy-crop'
import { cropToSquare } from './cropImage'
import { Modal } from './Modal'

/** 送る画像の一辺。サーバーで保存するサイズ（400px）と同じにする */
export const AVATAR_SIZE = 400

interface Props {
  file: File
  /** 切り抜いた画像を受け取る。保存が終わるまで「適用」は押せない */
  onApply: (image: Blob) => Promise<void>
  onClose: () => void
}

/**
 * アイコン画像の切り抜き（F-41）。ドラッグで位置、スライダー・ホイール・ピンチで拡大率を決める。
 */
export function AvatarCropper({ file, onApply, onClose }: Props) {
  const [src, setSrc] = useState<string | null>(null)
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [area, setArea] = useState<Area | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 選んだファイルを表示用の data URL として読み込む
  useEffect(() => {
    const reader = new FileReader()
    reader.onload = () => setSrc(typeof reader.result === 'string' ? reader.result : null)
    reader.onerror = () => setError('画像を読み込めませんでした')
    reader.readAsDataURL(file)
    return () => reader.abort()
  }, [file])

  const apply = async () => {
    if (!src || !area) return
    setSaving(true)
    setError(null)
    try {
      await onApply(await cropToSquare(src, area, AVATAR_SIZE))
    } catch (e) {
      setError(e instanceof Error ? e.message : '画像を保存できませんでした')
      setSaving(false)
    }
  }

  return (
    <Modal title="アイコン画像を切り抜く" onClose={saving ? () => {} : onClose}>
      <div className="relative h-72 overflow-hidden rounded-lg bg-slate-900">
        {src && (
          <Cropper
            image={src}
            crop={crop}
            zoom={zoom}
            aspect={1}
            cropShape="round"
            showGrid={false}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={(_, pixels) => setArea(pixels)}
          />
        )}
      </div>
      <label className="mt-4 flex items-center gap-3 text-sm text-slate-500">
        拡大
        <input
          type="range"
          min={1}
          max={3}
          step={0.01}
          value={zoom}
          onChange={(e) => setZoom(Number(e.target.value))}
          className="flex-1 accent-sky-500"
        />
      </label>
      {error && (
        <p role="alert" className="mt-3 text-sm text-red-600">
          {error}
        </p>
      )}
      <div className="mt-4 flex justify-end gap-2">
        <button
          type="button"
          onClick={onClose}
          disabled={saving}
          className="rounded-full border border-slate-300 px-4 py-2 text-sm font-bold hover:bg-slate-50 disabled:opacity-40"
        >
          キャンセル
        </button>
        <button
          type="button"
          onClick={() => void apply()}
          disabled={!area || saving}
          className="rounded-full bg-slate-900 px-4 py-2 text-sm font-bold text-white hover:bg-slate-700 disabled:opacity-40"
        >
          {saving ? '保存中…' : '適用'}
        </button>
      </div>
    </Modal>
  )
}
