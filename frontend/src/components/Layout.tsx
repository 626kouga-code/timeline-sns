import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { fetchUnreadCount, notificationKeys } from '../api/notifications'
import { useAuth } from '../auth/context'
import { Avatar } from './Avatar'
import { ComposeModal } from './ComposeModal'
import { Icon, type IconName } from './Icon'
import type { LayoutContext } from './layoutContext'

interface NavItem {
  to: string
  label: string
  icon: IconName
  end?: boolean
  /** 未読数など。0 なら出さない */
  badge?: number
}

/** 未読の通知数の確認間隔（F-51）。タイムラインの新着確認と同じ */
export const UNREAD_INTERVAL = 30_000

const badgeText = (count: number) => (count >= 100 ? '99+' : String(count))
const navLabel = (item: NavItem) => (item.badge ? `${item.label}（未読 ${badgeText(item.badge)} 件）` : item.label)

export function Layout() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  const [composeOpen, setComposeOpen] = useState(false)

  // 未読の通知数（F-51）。タブが非表示のあいだは確認しない
  const unread = useQuery({
    queryKey: notificationKeys.unreadCount(me?.id ?? ''),
    queryFn: fetchUnreadCount,
    enabled: !!me,
    refetchInterval: UNREAD_INTERVAL,
    refetchIntervalInBackground: false,
  })
  const unreadCount = unread.data?.count ?? 0

  const items: NavItem[] = me
    ? [
        { to: '/home', label: 'ホーム', icon: 'home' },
        { to: '/', label: '全体', icon: 'globe', end: true },
        { to: '/search', label: '検索', icon: 'search' },
        { to: '/notifications', label: '通知', icon: 'bell', badge: unreadCount },
        { to: `/users/${me.handle}`, label: 'プロフィール', icon: 'user' },
        { to: '/settings/profile', label: '設定', icon: 'settings' },
        ...(me.role === 'ADMIN' ? [{ to: '/admin/reports', label: '管理', icon: 'shield' as const }] : []),
      ]
    : [
        { to: '/', label: '全体', icon: 'globe', end: true },
        { to: '/search', label: '検索', icon: 'search' },
        { to: '/login', label: 'ログイン', icon: 'login' },
      ]

  // 先にトップへ移る。ログイン必須の画面のままログアウトすると、RequireAuth が /login へ飛ばしてしまう
  const handleLogout = async () => {
    navigate('/')
    try {
      await logout()
    } catch {
      // サーバーへの通知に失敗しても、手元のログイン状態は消えている
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
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={navClass}
                aria-label={item.badge ? navLabel(item) : undefined}
              >
                <span className="relative">
                  <Icon name={item.icon} className="size-7" />
                  {!!item.badge && (
                    <span className="absolute -top-1.5 -right-1.5 min-w-5 rounded-full bg-sky-500 px-1 text-center text-xs font-bold text-white">
                      {badgeText(item.badge)}
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
              aria-label="投稿する"
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
        <Outlet context={{ openCompose: () => setComposeOpen(true) } satisfies LayoutContext} />
      </main>

      {/* 下部ナビ（スマホ） */}
      <nav className="fixed inset-x-0 bottom-0 z-40 flex justify-around border-t border-slate-200 bg-white/95 py-1 backdrop-blur sm:hidden">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `relative p-3 ${isActive ? 'text-slate-900' : 'text-slate-500'}`}
            aria-label={navLabel(item)}
          >
            <Icon name={item.icon} className="size-6" />
            {!!item.badge && (
              <span className="absolute top-1 right-1 min-w-4 rounded-full bg-sky-500 px-1 text-center text-[10px] font-bold text-white">
                {badgeText(item.badge)}
              </span>
            )}
          </NavLink>
        ))}
        {me && (
          <button type="button" onClick={handleLogout} className="p-3 text-slate-500" aria-label="ログアウト">
            <Icon name="logout" className="size-6" />
          </button>
        )}
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

      {/* ログアウトしたら閉じる */}
      {composeOpen && me && <ComposeModal onClose={() => setComposeOpen(false)} />}
    </div>
  )
}
