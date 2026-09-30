import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { FollowButton } from '../components/FollowButton'
import { Menu, type MenuItem } from '../components/Menu'
import { PageHeader, Tabs } from '../components/PageHeader'
import { PostCard } from '../components/PostCard'
import { ReportDialog } from '../components/ReportDialog'
import { UserListItem } from '../components/UserListItem'
import { findUserByHandle, followersOf, followingOf, isBlocking, isFollowing, userPosts } from '../mock/selectors'
import { useStore } from '../mock/store'

function NotFound({ handle }: { handle?: string }) {
  return (
    <>
      <PageHeader title="プロフィール" back />
      <p className="px-4 py-16 text-center text-slate-500">@{handle} は存在しないか、表示できません。</p>
    </>
  )
}

// プロフィール（F-40, `/users/:handle`）
export function ProfilePage() {
  const { handle } = useParams()
  const { db, me, block, unblock, showToast } = useStore()
  const [reporting, setReporting] = useState(false)
  const user = handle ? findUserByHandle(db, handle) : undefined

  if (!user) return <NotFound handle={handle} />

  const isMe = me?.id === user.id
  const iBlock = !!me && isBlocking(db, me.id, user.id)
  const blocksMe = !!me && isBlocking(db, user.id, me.id)
  const followsMe = !!me && !isMe && isFollowing(db, user.id, me.id)

  // 凍結されたアカウントは投稿・プロフィールを非表示にする（F-63）
  if (user.status === 'SUSPENDED') {
    return (
      <>
        <PageHeader title="アカウント" back />
        <div className="h-32 bg-slate-200" />
        <div className="px-4 py-12 text-center">
          <p className="text-xl font-bold">@{user.handle}</p>
          <p className="mt-2 text-slate-500">このアカウントは凍結されています。</p>
        </div>
      </>
    )
  }

  const posts = userPosts(db, user.id, me?.id ?? null)
  const menuItems: MenuItem[] =
    !me || isMe
      ? []
      : [
          iBlock
            ? { label: `@${user.handle} のブロックを解除`, icon: 'ban', onSelect: () => unblock(user.id) }
            : {
                label: `@${user.handle} をブロック`,
                icon: 'ban',
                danger: true,
                onSelect: () => {
                  if (window.confirm(`@${user.handle} をブロックしますか？\nお互いの投稿が表示されなくなり、フォロー関係も解除されます。`)) {
                    block(user.id)
                    showToast(`@${user.handle} をブロックしました`)
                  }
                },
              },
          { label: `@${user.handle} を通報`, icon: 'flag', onSelect: () => setReporting(true) },
        ]

  return (
    <>
      <PageHeader title={user.displayName} subtitle={`${posts.length} 件の投稿`} back />
      <div className="h-36 bg-gradient-to-br from-sky-200 to-indigo-200" />
      <div className="px-4">
        <div className="-mt-12 flex items-end justify-between">
          <Avatar user={user} size="lg" />
          <div className="flex items-center gap-2 pb-1">
            <Menu items={menuItems} label="ユーザーのメニュー" />
            {isMe ? (
              <Link to="/settings/profile" className="rounded-full border border-slate-300 px-4 py-1.5 text-sm font-bold hover:bg-slate-50">
                プロフィールを編集
              </Link>
            ) : iBlock ? (
              <button
                type="button"
                onClick={() => unblock(user.id)}
                className="rounded-full bg-red-600 px-4 py-1.5 text-sm font-bold text-white hover:bg-red-700"
              >
                ブロック中
              </button>
            ) : (
              <FollowButton user={user} />
            )}
          </div>
        </div>
        <h2 className="mt-3 text-xl font-black">{user.displayName}</h2>
        <p className="flex items-center gap-2 text-slate-500">
          @{user.handle}
          {followsMe && <span className="rounded bg-slate-100 px-1 text-xs">フォローされています</span>}
        </p>
        {user.bio && <p className="mt-3 whitespace-pre-wrap">{user.bio}</p>}
        <div className="mt-3 flex gap-5 pb-3 text-sm">
          <Link to={`/users/${user.handle}/following`} className="hover:underline">
            <b>{followingOf(db, user.id).length}</b> <span className="text-slate-500">フォロー</span>
          </Link>
          <Link to={`/users/${user.handle}/followers`} className="hover:underline">
            <b>{followersOf(db, user.id).length}</b> <span className="text-slate-500">フォロワー</span>
          </Link>
        </div>
      </div>
      <div className="border-b border-slate-100">
        <Tabs tabs={[{ label: '投稿', active: true, onClick: () => {} }]} />
      </div>

      {iBlock || blocksMe ? (
        <div className="px-8 py-16 text-center">
          <p className="text-lg font-bold">{iBlock ? `@${user.handle} をブロックしています` : '投稿を表示できません'}</p>
          <p className="mt-2 text-sm text-slate-500">
            {iBlock ? 'ブロックを解除すると投稿が表示されます。' : 'このユーザーの投稿は表示できません。'}
          </p>
        </div>
      ) : posts.length === 0 ? (
        <p className="px-4 py-16 text-center text-slate-500">まだ投稿がありません</p>
      ) : (
        posts.map((post) => <PostCard key={post.id} post={post} />)
      )}

      {reporting && <ReportDialog targetType="USER" targetId={user.id} onClose={() => setReporting(false)} />}
    </>
  )
}

// フォロー・フォロワー一覧（F-43, `/users/:handle/following` `/users/:handle/followers`）
export function FollowListPage({ kind }: { kind: 'following' | 'followers' }) {
  const { handle } = useParams()
  const { db } = useStore()
  const navigate = useNavigate()
  const user = handle ? findUserByHandle(db, handle) : undefined

  if (!user || user.status === 'SUSPENDED') return <NotFound handle={handle} />

  const list = kind === 'following' ? followingOf(db, user.id) : followersOf(db, user.id)

  return (
    <>
      <PageHeader title={user.displayName} subtitle={`@${user.handle}`} back>
        <Tabs
          tabs={[
            { label: 'フォロー中', active: kind === 'following', onClick: () => navigate(`/users/${user.handle}/following`, { replace: true }) },
            { label: 'フォロワー', active: kind === 'followers', onClick: () => navigate(`/users/${user.handle}/followers`, { replace: true }) },
          ]}
        />
      </PageHeader>
      {list.length === 0 ? (
        <p className="px-4 py-16 text-center text-slate-500">
          {kind === 'following' ? 'まだ誰もフォローしていません' : 'まだフォロワーはいません'}
        </p>
      ) : (
        list.map((u) => <UserListItem key={u.id} user={u} />)
      )}
    </>
  )
}
