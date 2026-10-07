import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './auth/context'
import { Layout } from './components/Layout'
import { LoginPage, SignupPage } from './pages/AuthPages'
import { PlaceholderPage } from './pages/PlaceholderPage'
import { PostDetailPage } from './pages/PostDetailPage'
import { ProfileEditPage } from './pages/ProfileEditPage'
import { FollowListPage, ProfilePage } from './pages/ProfilePage'
import { SearchPage } from './pages/SearchPage'
import { TimelinePage } from './pages/TimelinePage'

function RequireAuth({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { me } = useAuth()
  const location = useLocation()
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (admin && me.role !== 'ADMIN') return <Navigate to="/home" replace />
  return children
}

// ログイン・登録画面。ログイン済みになったら、ログイン前に開こうとしていた画面（なければホーム）へ移る
function GuestOnly({ children }: { children: ReactNode }) {
  const { me } = useAuth()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/home'
  return me ? <Navigate to={from} replace /> : children
}

// パスは docs/04_screens.md の画面一覧に合わせる。準備中の画面は各 Issue で本来の画面に置き換える
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<TimelinePage mode="global" />} />
        <Route path="home" element={<RequireAuth><TimelinePage mode="home" /></RequireAuth>} />
        <Route path="login" element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path="signup" element={<GuestOnly><SignupPage /></GuestOnly>} />
        <Route path="posts/:id" element={<PostDetailPage />} />
        <Route path="users/:handle" element={<ProfilePage />} />
        <Route path="users/:handle/following" element={<FollowListPage kind="following" />} />
        <Route path="users/:handle/followers" element={<FollowListPage kind="followers" />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="settings/profile" element={<RequireAuth><ProfileEditPage /></RequireAuth>} />
        <Route path="notifications" element={<RequireAuth><PlaceholderPage title="通知" feature="F-50 通知一覧（#28）" /></RequireAuth>} />
        <Route path="admin/reports" element={<RequireAuth admin><PlaceholderPage title="通報一覧" feature="F-62 管理画面（#32）" /></RequireAuth>} />
        <Route path="verify-email" element={<PlaceholderPage title="メール確認" feature="F-02 メールアドレス確認（#8）" />} />
        <Route path="password-reset" element={<PlaceholderPage title="パスワード再設定" feature="F-05 パスワードリセット（#11）" />} />
        <Route path="onboarding" element={<PlaceholderPage title="ユーザーID設定" feature="F-04 Google ログイン（#10）" />} />
        <Route path="settings/account" element={<RequireAuth><PlaceholderPage title="アカウント設定" feature="パスワード変更・ブロック一覧・F-06 退会（#12）" /></RequireAuth>} />
        <Route path="*" element={<PlaceholderPage title="ページが見つかりません" feature="URL を確認してください" />} />
      </Route>
    </Routes>
  )
}
