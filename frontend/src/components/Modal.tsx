import { useEffect, type ReactNode } from 'react'
import { Icon } from './Icon'

interface Props {
  title: string
  onClose: () => void
  children: ReactNode
}

export function Modal({ title, onClose, children }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center bg-slate-900/40 px-4 pt-16"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="w-full max-w-lg rounded-2xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="flex items-center gap-2 border-b border-slate-100 px-4 py-2">
          <button type="button" onClick={onClose} className="rounded-full p-2 hover:bg-slate-100" aria-label="閉じる">
            <Icon name="x" />
          </button>
          <h2 className="font-bold">{title}</h2>
        </div>
        <div className="p-4">{children}</div>
      </div>
    </div>
  )
}
