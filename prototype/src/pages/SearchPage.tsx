import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { Icon } from '../components/Icon'
import { PageHeader } from '../components/PageHeader'
import { UserListItem } from '../components/UserListItem'
import { searchUsers } from '../mock/selectors'
import { useStore } from '../mock/store'

const PAGE_SIZE = 20

// ユーザー検索（F-44, `/search?q=`）
export function SearchPage() {
  const { db, me } = useStore()
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const [limit, setLimit] = useState(PAGE_SIZE)

  const results = searchUsers(db, query, me?.id ?? null)

  return (
    <>
      <PageHeader title="ユーザー検索">
        <div className="px-4 pb-3">
          <label className="flex items-center gap-2 rounded-full bg-slate-100 px-4 py-2 focus-within:bg-white focus-within:ring-2 focus-within:ring-sky-500">
            <Icon name="search" className="size-5 text-slate-500" />
            <input
              type="search"
              value={query}
              autoFocus
              onChange={(e) => {
                setLimit(PAGE_SIZE)
                setParams(e.target.value ? { q: e.target.value } : {}, { replace: true })
              }}
              placeholder="ユーザーID・表示名で検索"
              aria-label="ユーザーID・表示名で検索"
              className="w-full bg-transparent focus:outline-none"
            />
          </label>
        </div>
      </PageHeader>

      {!query.trim() ? (
        <p className="px-4 py-16 text-center text-slate-500">ユーザーID（@handle）や表示名の一部を入力してください</p>
      ) : results.length === 0 ? (
        <div className="px-8 py-16 text-center">
          <p className="text-lg font-bold">「{query}」に一致するユーザーはいません</p>
          <p className="mt-2 text-sm text-slate-500">別のキーワードで試してみてください。</p>
        </div>
      ) : (
        <>
          <p className="border-b border-slate-100 px-4 py-2 text-sm text-slate-500">{results.length} 件</p>
          {results.slice(0, limit).map((u) => (
            <UserListItem key={u.id} user={u} />
          ))}
          {results.length > limit && (
            <button
              type="button"
              onClick={() => setLimit((l) => l + PAGE_SIZE)}
              className="w-full py-4 text-sm font-semibold text-sky-600 hover:bg-slate-50"
            >
              さらに表示
            </button>
          )}
        </>
      )}
    </>
  )
}
