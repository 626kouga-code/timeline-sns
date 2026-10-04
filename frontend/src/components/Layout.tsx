import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/context'
import { Avatar } from './Avatar'
import { Icon, type IconName } from './Icon'

interface NavItem {
  to: string
  label: string
  icon: IconName
  end?: boolean
}

export function Layout() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()

  const items: NavItem[] = me
    ? [
        { to: '/home', label: 'ホーム', icon: 'home' },
        { to: '/', label: '全体', icon: 'globe', end: true },
        { to: '/search', label: '検索', icon: 'search' },
        { to: '/notifications', label: '通知', icon: 'bell' },
        { to: `/users/${me.handle}`, label: 'プロフィール', icon: 'user' },
        { to: '/settings/profile', label: '設定', icon: 'settings' },
        ...(me.role === 'ADMIN' ? [{ to: '/admin/reports', label: '管理', icon: 'shield' as const }] : []),
      ]
    : [
        { to: '/', label: '全体', icon: 'globe', end: true },
        { to: '/search', label: '検索', icon: 'search' },
        { to: '/login', label: 'ログイン', icon: 'login' },
      ]

  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      navigate('/')
    }
  }

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `relative flex items-center gap-4 rounded-full px-3 py-3 text-lg hover:bg-slate-100 ${isActive ? 'font-bold' : ''}`

  return (
    <div className="mx-auto flex min-h-screen max-w-6xl">
      {/* 左ナビ（PC・タブレット） */}
      <header className="sticky top-0 hidden h-screen w-20 shrink-0 flex-col justify-between px-2 py-4 sm:flex xl:w-64">
        <div>
          <NavLink to={me ? '/home' : '/'} className="mb-4 flex items-center gap-2 px-3 text-xl font-black text-sky-600">
            <span className="flex size-9 items-center justify-center rounded-xl bg-sky-500 text-white">t</span>
            <span className="hidden xl:inline">timeline</span>
          </NavLink>
          <nav className="space-y-1">
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
                <Icon name={item.icon} className="size-7" />
                <span className="hidden xl:inline">{item.label}</span>
              </NavLink>
            ))}
          </nav>
        </div>
        {me && (
          <div className="flex items-center gap-3 rounded-full p-2">
            <Avatar user={me} />
            <div className="hidden min-w-0 flex-1 xl:block">
              <p className="truncate text-sm font-bold">{me.displayName}</p>
              <p className="truncate text-sm text-slate-500">@{me.handle}</p>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-full p-2 text-slate-500 hover:bg-slate-100"
              aria-label="ログアウト"
              title="ログアウト"
            >
              <Icon name="logout" />
            </button>
          </div>
        )}
      </header>

      <main className="min-h-screen min-w-0 flex-1 border-slate-100 pb-20 sm:border-x sm:pb-0 lg:max-w-[600px]">
        <Outlet />
      </main>

      {/* 下部ナビ（スマホ） */}
      <nav className="fixed inset-x-0 bottom-0 z-40 flex justify-around border-t border-slate-200 bg-white/95 py-1 backdrop-blur sm:hidden">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `relative p-3 ${isActive ? 'text-slate-900' : 'text-slate-500'}`}
            aria-label={item.label}
          >
            <Icon name={item.icon} className="size-6" />
          </NavLink>
        ))}
        {me && (
          <button type="button" onClick={handleLogout} className="p-3 text-slate-500" aria-label="ログアウト">
            <Icon name="logout" className="size-6" />
          </button>
        )}
      </nav>
    </div>
  )
}
