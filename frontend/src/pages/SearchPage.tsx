import { useInfiniteQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { searchUsers, userKeys } from '../api/users'
import { useAuth } from '../auth/context'
import { Icon } from '../components/Icon'
import { LoadMore } from '../components/LoadMore'
import { PageHeader } from '../components/PageHeader'
import { UserListItem } from '../components/UserListItem'

/** 入力が止まってから問い合わせるまでの時間 */
export const SEARCH_DEBOUNCE = 300

// ユーザー検索（F-44, `/search?q=`）。ゲストも使える
export function SearchPage() {
  const { me } = useAuth()
  const [params, setParams] = useSearchParams()
  const query = (params.get('q') ?? '').trim()
  const [text, setText] = useState(params.get('q') ?? '')

  // 入力が止まってから URL（?q=）に反映する。URL のキーワードで検索する
  useEffect(() => {
    const timer = setTimeout(() => {
      if (text.trim() !== query) setParams(text.trim() ? { q: text.trim() } : {}, { replace: true })
    }, SEARCH_DEBOUNCE)
    return () => clearTimeout(timer)
  }, [text, query, setParams])

  const results = useInfiniteQuery({
    queryKey: userKeys.search(query, me?.id ?? 'guest'),
    queryFn: ({ pageParam }) => searchUsers(query, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: query.length > 0,
  })
  const users = results.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <>
      <PageHeader title="ユーザー検索">
        <div className="px-4 pb-3">
          <label className="flex items-center gap-2 rounded-full bg-slate-100 px-4 py-2 focus-within:bg-white focus-within:ring-2 focus-within:ring-sky-500">
            <Icon name="search" className="size-5 text-slate-500" />
            <input
              type="search"
              value={text}
              autoFocus
              maxLength={50}
              onChange={(e) => setText(e.target.value)}
              placeholder="ユーザーID・表示名で検索"
              aria-label="ユーザーID・表示名で検索"
              className="w-full bg-transparent focus:outline-none"
            />
          </label>
        </div>
      </PageHeader>

      {!query ? (
        <p className="px-4 py-16 text-center text-slate-500">ユーザーID（@handle）や表示名の一部を入力してください</p>
      ) : results.isPending ? (
        <p className="py-10 text-center text-sm text-slate-400">検索中…</p>
      ) : results.isError && users.length === 0 ? (
        <div className="px-8 py-16 text-center">
          <p role="alert" className="text-slate-600">
            {results.error.message}
          </p>
          <button
            type="button"
            onClick={() => void results.refetch()}
            className="mt-4 rounded-full border border-slate-300 px-4 py-2 text-sm font-bold hover:bg-slate-50"
          >
            再読み込み
          </button>
        </div>
      ) : users.length === 0 ? (
        <div className="px-8 py-16 text-center">
          <p className="text-lg font-bold break-words">「{query}」に一致するユーザーはいません</p>
          <p className="mt-2 text-sm text-slate-500">別のキーワードで試してみてください。</p>
        </div>
      ) : (
        <>
          {users.map((u) => (
            <UserListItem key={u.id} user={u} />
          ))}
          <LoadMore
            hasNextPage={results.hasNextPage}
            isFetchingNextPage={results.isFetchingNextPage}
            fetchNextPage={results.fetchNextPage}
            isEmpty={false}
          />
        </>
      )}
    </>
  )
}
