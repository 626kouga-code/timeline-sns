import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { Icon } from './Icon'

interface Props {
  title: string
  subtitle?: string
  back?: boolean
  children?: ReactNode
}

export function PageHeader({ title, subtitle, back = false, children }: Props) {
  const navigate = useNavigate()
  return (
    <div className="sticky top-0 z-20 border-b border-slate-100 bg-white/90 backdrop-blur">
      <div className="flex items-center gap-4 px-4 py-2.5">
        {back && (
          <button type="button" onClick={() => navigate(-1)} className="rounded-full p-2 hover:bg-slate-100" aria-label="戻る">
            <Icon name="back" />
          </button>
        )}
        <div className="min-w-0">
          <h1 className="truncate text-lg font-bold">{title}</h1>
          {subtitle && <p className="truncate text-xs text-slate-500">{subtitle}</p>}
        </div>
      </div>
      {children}
    </div>
  )
}

export function Tabs({ tabs }: { tabs: { label: string; active: boolean; onClick: () => void }[] }) {
  return (
    <div className="flex">
      {tabs.map((tab) => (
        <button
          key={tab.label}
          type="button"
          onClick={tab.onClick}
          className="flex flex-1 justify-center py-3 text-sm font-semibold text-slate-500 hover:bg-slate-50"
        >
          <span className={`relative py-1 ${tab.active ? 'text-slate-900' : ''}`}>
            {tab.label}
            {tab.active && <span className="absolute inset-x-0 -bottom-3 h-1 rounded-full bg-sky-500" />}
          </span>
        </button>
      ))}
    </div>
  )
}
