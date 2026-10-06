import { useEffect, useRef } from 'react'

interface Props {
  hasNextPage: boolean
  isFetchingNextPage: boolean
  fetchNextPage: () => unknown
  /** 最後まで読み込んだときに出す文言。一覧が空なら出さない */
  endMessage?: string
  isEmpty: boolean
}

/**
 * 無限スクロールの末尾。画面下端が見えたら次のページを読み込む。
 * IntersectionObserver が使えない環境向けに、押して読み込むボタンも兼ねる。
 */
export function LoadMore({ hasNextPage, isFetchingNextPage, fetchNextPage, endMessage, isEmpty }: Props) {
  const sentinel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = sentinel.current
    if (!el || !hasNextPage || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !isFetchingNextPage) void fetchNextPage()
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  if (hasNextPage) {
    return (
      <div ref={sentinel} className="py-6 text-center">
        <button
          type="button"
          onClick={() => void fetchNextPage()}
          disabled={isFetchingNextPage}
          className="text-sm text-slate-400 hover:text-sky-600"
        >
          {isFetchingNextPage ? '読み込み中…' : 'さらに読み込む'}
        </button>
      </div>
    )
  }
  if (isEmpty || !endMessage) return null
  return <p className="py-8 text-center text-sm text-slate-400">{endMessage}</p>
}
