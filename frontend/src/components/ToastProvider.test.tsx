import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from './ToastProvider'
import { useToast } from './toast'

function Buttons() {
  const showToast = useToast()
  return (
    <>
      <button type="button" onClick={() => showToast('1 つ目')}>
        1 つ目を出す
      </button>
      <button type="button" onClick={() => showToast('2 つ目')}>
        2 つ目を出す
      </button>
    </>
  )
}

// フェイクタイマーの間は userEvent が進まないので、クリックは fireEvent で起こす
const click = (name: string) => fireEvent.click(screen.getByRole('button', { name }))

describe('ToastProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('メッセージを status として出し、2.5 秒で消す', () => {
    render(
      <ToastProvider>
        <Buttons />
      </ToastProvider>,
    )
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    click('1 つ目を出す')
    expect(screen.getByRole('status')).toHaveTextContent('1 つ目')

    act(() => vi.advanceTimersByTime(2499))
    expect(screen.getByRole('status')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(1))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('続けて出すと新しいメッセージに替え、そこから 2.5 秒表示する', () => {
    render(
      <ToastProvider>
        <Buttons />
      </ToastProvider>,
    )

    click('1 つ目を出す')
    act(() => vi.advanceTimersByTime(2000))
    click('2 つ目を出す')
    act(() => vi.advanceTimersByTime(2000))

    expect(screen.getByRole('status')).toHaveTextContent('2 つ目')
    act(() => vi.advanceTimersByTime(500))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('Provider の外では何もしない（エラーにならない）', () => {
    render(<Buttons />)

    expect(() => click('1 つ目を出す')).not.toThrow()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
