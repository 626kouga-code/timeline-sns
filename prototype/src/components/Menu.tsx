import { useEffect, useRef, useState } from 'react'
import { Icon, type IconName } from './Icon'

export interface MenuItem {
  label: string
  icon: IconName
  danger?: boolean
  onSelect: () => void
}

// 投稿・コメント・プロフィールの「…」メニュー
export function Menu({ items, label = 'メニュー' }: { items: MenuItem[]; label?: string }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  if (items.length === 0) return null

  return (
    <div className="relative" ref={ref} onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        className="rounded-full p-1.5 text-slate-500 hover:bg-sky-50 hover:text-sky-600"
        onClick={() => setOpen((o) => !o)}
        aria-label={label}
        aria-expanded={open}
      >
        <Icon name="dots" className="size-5" />
      </button>
      {open && (
        <ul className="absolute right-0 z-30 mt-1 w-52 overflow-hidden rounded-xl border border-slate-100 bg-white py-1 shadow-lg">
          {items.map((item) => (
            <li key={item.label}>
              <button
                type="button"
                className={`flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm font-semibold hover:bg-slate-50 ${
                  item.danger ? 'text-red-600' : ''
                }`}
                onClick={() => {
                  setOpen(false)
                  item.onSelect()
                }}
              >
                <Icon name={item.icon} className="size-4" />
                {item.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
