import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { timelineKeys } from '../api/timeline'
import { fetchFollowList, fetchUserPosts, getProfile, userKeys, type FollowListKind } from '../api/users'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { FollowButton } from '../components/FollowButton'
import { LoadMore } from '../components/LoadMore'
import { PageHeader, Tabs } from '../components/PageHeader'
import { PostCard } from '../components/PostCard'
import { UserListItem } from '../components/UserListItem'

const isNotFound = (error: unknown) => error instanceof ApiError && error.status === 404

function NotFound({ handle }: { handle?: string }) {
  return (
    <>
      <PageHeader title="プロフィール" back />
      <p className="px-4 py-16 text-center text-slate-500">@{handle} は存在しないか、表示できません。</p>
    </>
  )
}

function Loading() {
  return <p className="py-10 text-center text-sm text-slate-400">読み込み中…</p>
}

function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="px-8 py-16 text-center">
      <p role="alert" className="text-slate-600">
        {message}
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-4 rounded-full border border-slate-300 px-4 py-2 text-sm font-bold hover:bg-slate-50"
      >
        再読み込み
      </button>
    </div>
  )
}

// プロフィール（F-40, `/users/:handle`）。ブロック・通報のメニューは #30・#31 で追加する
export function ProfilePage() {
  const { handle = '' } = useParams()
  const { me } = useAuth()
  const viewer = me?.id ?? 'guest'

  const profile = useQuery({ queryKey: userKeys.profile(handle, viewer), queryFn: () => getProfile(handle) })
  const user = profile.data
  // ブロック関係にあると投稿は返ってこない（F-60）ので、問い合わせない
  const blocked = !!user && (user.blocking || user.blockedBy)

  const posts = useInfiniteQuery({
    queryKey: timelineKeys.user(handle, viewer),
    queryFn: ({ pageParam }) => fetchUserPosts(handle, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: !!user && !blocked,
  })
  const items = posts.data?.pages.flatMap((page) => page.items) ?? []

  if (profile.isPending) {
    return (
      <>
        <PageHeader title="プロフィール" back />
        <Loading />
      </>
    )
  }
  if (profile.isError) {
    if (isNotFound(profile.error)) return <NotFound handle={handle} />
    return (
      <>
        <PageHeader title="プロフィール" back />
        <LoadError message={profile.error.message} onRetry={() => void profile.refetch()} />
      </>
    )
  }

  const isMe = me?.id === profile.data.id
  const u = profile.data

  return (
    <>
      <PageHeader title={u.displayName} subtitle={`${u.postCount} 件の投稿`} back />
      <div className="h-36 bg-gradient-to-br from-sky-200 to-indigo-200" />
      <div className="px-4">
        <div className="-mt-12 flex items-end justify-between">
          <Avatar user={u} size="lg" />
          <div className="flex items-center gap-2 pb-1">
            {isMe ? (
              <Link
                to="/settings/profile"
                className="rounded-full border border-slate-300 px-4 py-1.5 text-sm font-bold hover:bg-slate-50"
              >
                プロフィールを編集
              </Link>
            ) : (
              !blocked && <FollowButton user={u} />
            )}
          </div>
        </div>
        <h2 className="mt-3 text-xl font-black break-words">{u.displayName}</h2>
        <p className="flex items-center gap-2 text-slate-500">
          @{u.handle}
          {!!me && !isMe && u.followedBy &&<span className="rounded bg-slate-100 px-1 text-xs">フォローされています</span>}
        </p>
        {u.bio && <p className="mt-3 break-words whitespace-pre-wrap">{u.bio}</p>}
        <div className="mt-3 flex gap-5 pb-3 text-sm">
          <Link to={`/users/${u.handle}/following`} className="hover:underline">
            <b>{u.followingCount}</b> <span className="text-slate-500">フォロー</span>
          </Link>
          <Link to={`/users/${u.handle}/followers`} className="hover:underline">
            <b>{u.followerCount}</b> <span className="text-slate-500">フォロワー</span>
          </Link>
        </div>
      </div>
      <div className="border-b border-slate-100">
        <Tabs tabs={[{ label: '投稿', active: true, onClick: () => {} }]} />
      </div>

      {blocked ? (
        <div className="px-8 py-16 text-center">
          <p className="text-lg font-bold">{u.blocking ? `@${u.handle} をブロックしています` : '投稿を表示できません'}</p>
          <p className="mt-2 text-sm text-slate-500">
            {u.blocking ? 'ブロックを解除すると投稿が表示されます。' : 'このユーザーの投稿は表示できません。'}
          </p>
        </div>
      ) : (
        <>
          {posts.isPending && <Loading />}
          {posts.isError && items.length === 0 && (
            <LoadError message={posts.error.message} onRetry={() => void posts.refetch()} />
          )}
          {items.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
          {posts.isSuccess && items.length === 0 && (
            <p className="px-4 py-16 text-center text-slate-500">まだ投稿がありません</p>
          )}
          <LoadMore
            hasNextPage={posts.hasNextPage}
            isFetchingNextPage={posts.isFetchingNextPage}
            fetchNextPage={posts.fetchNextPage}
            endMessage="これ以上の投稿はありません"
            isEmpty={items.length === 0}
          />
        </>
      )}
    </>
  )
}

// フォロー・フォロワー一覧（F-43, `/users/:handle/following` `/users/:handle/followers`）
export function FollowListPage({ kind }: { kind: FollowListKind }) {
  const { handle = '' } = useParams()
  const { me } = useAuth()
  const navigate = useNavigate()
  const viewer = me?.id ?? 'guest'

  // 見出しに表示名を出すためにプロフィールも取る（プロフィール画面から来ればキャッシュがある）
  const profile = useQuery({ queryKey: userKeys.profile(handle, viewer), queryFn: () => getProfile(handle) })
  const list = useInfiniteQuery({
    queryKey: userKeys.followList(handle, kind, viewer),
    queryFn: ({ pageParam }) => fetchFollowList(handle, kind, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const users = list.data?.pages.flatMap((page) => page.items) ?? []

  if (isNotFound(profile.error) || isNotFound(list.error)) return <NotFound handle={handle} />

  return (
    <>
      <PageHeader title={profile.data?.displayName ?? `@${handle}`} subtitle={`@${profile.data?.handle ?? handle}`} back>
        <Tabs
          tabs={[
            {
              label: 'フォロー中',
              active: kind === 'following',
              onClick: () => navigate(`/users/${handle}/following`, { replace: true }),
            },
            {
              label: 'フォロワー',
              active: kind === 'followers',
              onClick: () => navigate(`/users/${handle}/followers`, { replace: true }),
            },
          ]}
        />
      </PageHeader>

      {list.isPending && <Loading />}
      {list.isError && users.length === 0 && <LoadError message={list.error.message} onRetry={() => void list.refetch()} />}
      {users.map((u) => (
        <UserListItem key={u.id} user={u} />
      ))}
      {list.isSuccess && users.length === 0 && (
        <p className="px-4 py-16 text-center text-slate-500">
          {kind === 'following' ? 'まだ誰もフォローしていません' : 'まだフォロワーはいません'}
        </p>
      )}
      <LoadMore
        hasNextPage={list.hasNextPage}
        isFetchingNextPage={list.isFetchingNextPage}
        fetchNextPage={list.fetchNextPage}
        isEmpty={users.length === 0}
      />
    </>
  )
}
