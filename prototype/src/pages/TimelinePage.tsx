import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { useLayout } from '../components/Layout'
import { PageHeader, Tabs } from '../components/PageHeader'
import { PostCard } from '../components/PostCard'
import { globalTimeline, homeTimeline } from '../mock/selectors'
import { useStore } from '../mock/store'

const PAGE_SIZE = 20

// 全体タイムライン（F-21, `/`）とホームタイムライン（F-20, `/home`）
export function TimelinePage({ mode }: { mode: 'global' | 'home' }) {
  const { db, me } = useStore()
  const { openCompose } = useLayout()
  const navigate = useNavigate()
  const [limit, setLimit] = useState(PAGE_SIZE)
  const sentinel = useRef<HTMLDivElement>(null)

  const posts = mode === 'home' && me ? homeTimeline(db, me.id) : globalTimeline(db, me?.id ?? null)
  const visible = posts.slice(0, limit)
  const hasMore = posts.length > limit

  // 画面下端が見えたら次の 20 件を表示する（無限スクロール）
  useEffect(() => {
    const el = sentinel.current
    if (!el || !hasMore) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) setLimit((l) => l + PAGE_SIZE)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasMore])

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

      {visible.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}

      {posts.length === 0 && (
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

      {hasMore ? (
        <div ref={sentinel} className="py-6 text-center text-sm text-slate-400">
          読み込み中…
        </div>
      ) : (
        posts.length > 0 && <p className="py-8 text-center text-sm text-slate-400">これ以上の投稿はありません</p>
      )}
    </>
  )
}
