import { useState } from 'react'
import { NavLink, Outlet, useNavigate, useOutletContext } from 'react-router'
import { unreadCount } from '../mock/selectors'
import { useStore } from '../mock/store'
import { Avatar } from './Avatar'
import { ComposeModal } from './ComposeModal'
import { Icon, type IconName } from './Icon'

interface LayoutContext {
  openCompose: () => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const useLayout = () => useOutletContext<LayoutContext>()

interface NavItem {
  to: string
  label: string
  icon: IconName
  badge?: number
  end?: boolean
}

export function Layout() {
  const { db, me, logout, toast } = useStore()
  const navigate = useNavigate()
  const [composeOpen, setComposeOpen] = useState(false)

  const items: NavItem[] = me
    ? [
        { to: '/home', label: 'ホーム', icon: 'home' },
        { to: '/', label: '全体', icon: 'globe', end: true },
        { to: '/notifications', label: '通知', icon: 'bell', badge: unreadCount(db, me.id) },
        { to: `/users/${me.handle}`, label: 'プロフィール', icon: 'user' },
        { to: '/settings/profile', label: '設定', icon: 'settings' },
        ...(me.role === 'ADMIN' ? [{ to: '/admin/reports', label: '管理', icon: 'shield' as const }] : []),
      ]
    : [
        { to: '/', label: '全体', icon: 'globe', end: true },
        { to: '/login', label: 'ログイン', icon: 'login' },
      ]

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
                <span className="relative">
                  <Icon name={item.icon} className="size-7" />
                  {!!item.badge && (
                    <span className="absolute -top-1.5 -right-1.5 min-w-5 rounded-full bg-sky-500 px-1 text-center text-xs font-bold text-white">
                      {item.badge}
                    </span>
                  )}
                </span>
                <span className="hidden xl:inline">{item.label}</span>
              </NavLink>
            ))}
          </nav>
          {me && (
            <button
              type="button"
              onClick={() => setComposeOpen(true)}
              className="mt-4 flex w-full items-center justify-center gap-2 rounded-full bg-sky-500 py-3 font-bold text-white shadow hover:bg-sky-600"
            >
              <Icon name="pen" className="size-6 xl:hidden" />
              <span className="hidden xl:inline">投稿する</span>
            </button>
          )}
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
              onClick={() => {
                logout()
                navigate('/')
              }}
              className="hidden rounded-full p-2 text-slate-500 hover:bg-slate-100 xl:block"
              aria-label="ログアウト"
              title="ログアウト"
            >
              <Icon name="logout" />
            </button>
          </div>
        )}
      </header>

      <main className="min-h-screen min-w-0 flex-1 border-slate-100 pb-20 sm:border-x sm:pb-0 lg:max-w-[600px]">
        <Outlet context={{ openCompose: () => setComposeOpen(true) } satisfies LayoutContext} />
      </main>

      <PrototypePanel />

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
            {!!item.badge && (
              <span className="absolute top-1 right-1 min-w-4 rounded-full bg-sky-500 px-1 text-center text-[10px] font-bold text-white">
                {item.badge}
              </span>
            )}
          </NavLink>
        ))}
      </nav>
      {me && (
        <button
          type="button"
          onClick={() => setComposeOpen(true)}
          className="fixed right-4 bottom-20 z-40 rounded-full bg-sky-500 p-4 text-white shadow-lg sm:hidden"
          aria-label="投稿する"
        >
          <Icon name="pen" className="size-6" />
        </button>
      )}

      {composeOpen && <ComposeModal onClose={() => setComposeOpen(false)} />}
      {toast && (
        <div className="fixed bottom-24 left-1/2 z-[60] -translate-x-1/2 rounded-full bg-sky-500 px-5 py-2.5 text-sm font-semibold text-white shadow-lg sm:bottom-8">
          {toast}
        </div>
      )}
    </div>
  )
}

// 画面確認用のパネル（本番には存在しない）。ユーザーの切り替えとデータの初期化ができる
function PrototypePanel() {
  const { db, me, loginAs, logout, reset, showToast } = useStore()
  const navigate = useNavigate()

  return (
    <aside className="sticky top-0 hidden h-screen w-80 shrink-0 space-y-4 overflow-y-auto px-6 py-4 lg:block">
      <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
        <h2 className="font-bold text-amber-900">プロトタイプ操作</h2>
        <p className="mt-1 text-xs text-amber-800">
          バックエンドなしのモックです。データはメモリ上のみで、リロードすると初期状態に戻ります。
        </p>
        <p className="mt-3 text-xs font-semibold text-amber-900">ユーザーを切り替える</p>
        <ul className="mt-1 space-y-1">
          {db.users.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                onClick={() => {
                  const result = loginAs(u.id)
                  if (result.ok) navigate('/home')
                  else showToast(result.error)
                }}
                className={`flex w-full items-center gap-2 rounded-lg px-2 py-1 text-left text-sm hover:bg-amber-100 ${
                  me?.id === u.id ? 'bg-amber-100 font-bold' : ''
                }`}
              >
                <Avatar user={u} size="sm" />
                <span className="min-w-0 flex-1 truncate">
                  {u.displayName} <span className="text-slate-500">@{u.handle}</span>
                </span>
                {u.role === 'ADMIN' && <span className="rounded bg-slate-900 px-1 text-[10px] text-white">管理者</span>}
                {u.status === 'SUSPENDED' && <span className="rounded bg-red-600 px-1 text-[10px] text-white">凍結</span>}
              </button>
            </li>
          ))}
        </ul>
        <div className="mt-3 flex gap-2">
          {me && (
            <button
              type="button"
              onClick={() => {
                logout()
                navigate('/')
              }}
              className="flex-1 rounded-full border border-amber-300 bg-white py-1.5 text-sm font-semibold hover:bg-amber-100"
            >
              ゲストで見る
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              reset()
              navigate('/')
              showToast('データを初期化しました')
            }}
            className="flex-1 rounded-full border border-amber-300 bg-white py-1.5 text-sm font-semibold hover:bg-amber-100"
          >
            データを初期化
          </button>
        </div>
      </section>
      <p className="px-2 text-xs text-slate-400">
        画面は docs/04_screens.md、仕様は docs/02_functional-requirements.md の Must に準拠。
      </p>
    </aside>
  )
}
