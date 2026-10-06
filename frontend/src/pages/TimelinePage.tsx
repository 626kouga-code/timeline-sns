import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { fetchNewCount, fetchTimeline, timelineKeys, type TimelineMode } from '../api/timeline'
import { useAuth } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { useLayout } from '../components/layoutContext'
import { LoadMore } from '../components/LoadMore'
import { PageHeader, Tabs } from '../components/PageHeader'
import { PostCard } from '../components/PostCard'

/** 新着の確認間隔（F-22） */
export const NEW_POSTS_INTERVAL = 30_000

// 全体タイムライン（F-21, `/`）とホームタイムライン（F-20, `/home`）
export function TimelinePage({ mode }: { mode: TimelineMode }) {
  const { me } = useAuth()
  const { openCompose } = useLayout()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const viewer = me?.id ?? 'guest'

  const timeline = useInfiniteQuery({
    queryKey: timelineKeys.list(mode, viewer),
    queryFn: ({ pageParam }) => fetchTimeline(mode, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    // 投稿が 1 件もないと新着件数を問い合わせられない（since が無い）ので、一覧ごと取り直す
    refetchInterval: (query) => (query.state.data?.pages[0]?.items.length ? false : NEW_POSTS_INTERVAL),
    refetchIntervalInBackground: false,
  })
  const posts = timeline.data?.pages.flatMap((page) => page.items) ?? []
  const firstId = posts[0]?.id

  // 表示中の先頭より新しい投稿の数（F-22）。最初の確認は 30 秒後、タブが非表示のあいだは止まる
  const newCount = useQuery({
    queryKey: timelineKeys.newCount(mode, viewer, firstId),
    queryFn: () => fetchNewCount(mode, firstId as string),
    enabled: firstId !== undefined,
    initialData: { count: 0 },
    staleTime: NEW_POSTS_INTERVAL,
    refetchInterval: NEW_POSTS_INTERVAL,
    refetchIntervalInBackground: false,
  })
  const newPosts = newCount.data.count

  const showNewPosts = () => {
    window.scrollTo({ top: 0 })
    queryClient.setQueryData(timelineKeys.newCount(mode, viewer, firstId), { count: 0 })
    // 2 ページ目以降は捨てて、先頭のページから取り直す
    void queryClient.resetQueries({ queryKey: timelineKeys.list(mode, viewer), exact: true })
  }

  return (
    <>
      <PageHeader title={mode === 'home' ? 'ホーム' : '全体タイムライン'}>
        {me && (
          <Tabs
            tabs={[
              { label: 'フォロー中', active: mode === 'home', onClick: () => navigate('/home') },
              { label: 'すべての投稿', active: mode === 'global', onClick: () => navigate('/') },
            ]}
          />
        )}
        {newPosts > 0 && (
          <div className="flex justify-center pb-2">
            <button
              type="button"
              onClick={showNewPosts}
              className="rounded-full bg-sky-500 px-4 py-1.5 text-sm font-bold text-white shadow hover:bg-sky-600"
            >
              新しい投稿があります（{newPosts >= 100 ? '99+' : newPosts} 件）
            </button>
          </div>
        )}
      </PageHeader>

      {!me && (
        <div className="border-b border-slate-100 bg-sky-50 px-4 py-5">
          <p className="font-bold">いま起きていることを見つけよう</p>
          <p className="mt-1 text-sm text-slate-600">登録すると投稿・いいね・フォローができます。</p>
          <div className="mt-3 flex gap-2">
            <Link to="/signup" className="rounded-full bg-sky-500 px-4 py-2 text-sm font-bold text-white hover:bg-sky-600">
              新規登録
            </Link>
            <Link to="/login" className="rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-bold hover:bg-slate-50">
              ログイン
            </Link>
          </div>
        </div>
      )}

      {me && (
        <button
          type="button"
          onClick={openCompose}
          className="flex w-full items-center gap-3 border-b border-slate-100 px-4 py-3 text-left hover:bg-slate-50/70"
        >
          <Avatar user={me} />
          <span className="flex-1 text-lg text-slate-400">いまどうしてる？</span>
          <span className="rounded-full bg-sky-500 px-4 py-1.5 text-sm font-bold text-white">投稿する</span>
        </button>
      )}

      {timeline.isPending && <p className="py-10 text-center text-sm text-slate-400">読み込み中…</p>}

      {timeline.isError && posts.length === 0 && (
        <div className="px-8 py-16 text-center">
          <p role="alert" className="text-slate-600">
            {timeline.error.message}
          </p>
          <button
            type="button"
            onClick={() => void timeline.refetch()}
            className="mt-4 rounded-full border border-slate-300 px-4 py-2 text-sm font-bold hover:bg-slate-50"
          >
            再読み込み
          </button>
        </div>
      )}

      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}

      {timeline.isSuccess && posts.length === 0 && (
        <div className="px-8 py-16 text-center">
          <p className="text-lg font-bold">まだ投稿がありません</p>
          {mode === 'home' && (
            <p className="mt-2 text-sm text-slate-500">
              <Link to="/" className="text-sky-600 hover:underline">
                全体タイムライン
              </Link>
              からユーザーを探してフォローしてみましょう。
            </p>
          )}
        </div>
      )}

      {/* 画面下端が見えたら次の 20 件を読み込む（無限スクロール） */}
      <LoadMore
        hasNextPage={timeline.hasNextPage}
        isFetchingNextPage={timeline.isFetchingNextPage}
        fetchNextPage={timeline.fetchNextPage}
        endMessage="これ以上の投稿はありません"
        isEmpty={posts.length === 0}
      />
    </>
  )
}
