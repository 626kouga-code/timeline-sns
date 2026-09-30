import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { Layout } from './components/Layout'
import { useStore } from './mock/store'
import { AdminReportsPage } from './pages/AdminReportsPage'
import { LoginPage, SignupPage } from './pages/AuthPages'
import { NotificationsPage } from './pages/NotificationsPage'
import { PlaceholderPage } from './pages/PlaceholderPage'
import { PostDetailPage } from './pages/PostDetailPage'
import { ProfileEditPage } from './pages/ProfileEditPage'
import { FollowListPage, ProfilePage } from './pages/ProfilePage'
import { TimelinePage } from './pages/TimelinePage'

function RequireAuth({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { me } = useStore()
  const location = useLocation()
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (admin && me.role !== 'ADMIN') return <Navigate to="/home" replace />
  return children
}

function GuestOnly({ children }: { children: ReactNode }) {
  const { me } = useStore()
  return me ? <Navigate to="/home" replace /> : children
}

// パスは docs/04_screens.md の画面一覧に合わせる
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<TimelinePage key="global" mode="global" />} />
        <Route path="home" element={<RequireAuth><TimelinePage key="home" mode="home" /></RequireAuth>} />
        <Route path="login" element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path="signup" element={<GuestOnly><SignupPage /></GuestOnly>} />
        <Route path="posts/:id" element={<PostDetailPage />} />
        <Route path="users/:handle" element={<ProfilePage />} />
        <Route path="users/:handle/following" element={<FollowListPage kind="following" />} />
        <Route path="users/:handle/followers" element={<FollowListPage kind="followers" />} />
        <Route path="settings/profile" element={<RequireAuth><ProfileEditPage /></RequireAuth>} />
        <Route path="notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
        <Route path="admin/reports" element={<RequireAuth admin><AdminReportsPage /></RequireAuth>} />
        <Route path="verify-email" element={<PlaceholderPage title="メール確認" feature="F-02 メールアドレス確認" />} />
        <Route path="password-reset" element={<PlaceholderPage title="パスワード再設定" feature="F-05 パスワードリセット" />} />
        <Route path="onboarding" element={<PlaceholderPage title="ユーザーID設定" feature="F-04 Google ログイン初回のユーザーID設定" />} />
        <Route path="settings/account" element={<PlaceholderPage title="アカウント設定" feature="パスワード変更・ブロック一覧・F-06 退会" />} />
        <Route path="*" element={<PlaceholderPage title="ページが見つかりません" feature="URL を確認してください" />} />
      </Route>
    </Routes>
  )
}
