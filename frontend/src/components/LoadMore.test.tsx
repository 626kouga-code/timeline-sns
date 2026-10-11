import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { LoadMore } from './LoadMore'

describe('LoadMore', () => {
  it('次のページがあれば、押して読み込むボタンを出す', async () => {
    const fetchNextPage = vi.fn()
    render(<LoadMore hasNextPage isFetchingNextPage={false} fetchNextPage={fetchNextPage} isEmpty={false} />)

    await userEvent.click(screen.getByRole('button', { name: 'さらに読み込む' }))

    expect(fetchNextPage).toHaveBeenCalledOnce()
  })

  it('読み込み中は押せない', () => {
    render(<LoadMore hasNextPage isFetchingNextPage fetchNextPage={vi.fn()} isEmpty={false} />)

    expect(screen.getByRole('button', { name: '読み込み中…' })).toBeDisabled()
  })

  it('最後まで読み込んだら終わりの文言を出す', () => {
    render(
      <LoadMore
        hasNextPage={false}
        isFetchingNextPage={false}
        fetchNextPage={vi.fn()}
        endMessage="これ以上の投稿はありません"
        isEmpty={false}
      />,
    )

    expect(screen.getByText('これ以上の投稿はありません')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('一覧が空なら終わりの文言も出さない', () => {
    const { container } = render(
      <LoadMore hasNextPage={false} isFetchingNextPage={false} fetchNextPage={vi.fn()} endMessage="終わり" isEmpty />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('画面の下端が見えたら自動で次のページを読み込む', () => {
    let notify: (entries: { isIntersecting: boolean }[]) => void = () => {}
    const observe = vi.fn()
    const disconnect = vi.fn()
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(callback: typeof notify) {
          notify = callback
        }
        observe = observe
        disconnect = disconnect
      },
    )
    const fetchNextPage = vi.fn()
    const { unmount } = render(
      <LoadMore hasNextPage isFetchingNextPage={false} fetchNextPage={fetchNextPage} isEmpty={false} />,
    )

    expect(observe).toHaveBeenCalledOnce()
    act(() => notify([{ isIntersecting: false }]))
    expect(fetchNextPage).not.toHaveBeenCalled()
    act(() => notify([{ isIntersecting: true }]))
    expect(fetchNextPage).toHaveBeenCalledOnce()

    unmount()
    expect(disconnect).toHaveBeenCalled()
  })
})
