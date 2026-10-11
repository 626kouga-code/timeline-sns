import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders, user } from '../test/utils'
import { useRequireLogin } from './useRequireLogin'

function Button({ action }: { action: () => void }) {
  const requireLogin = useRequireLogin()
  return (
    <button type="button" onClick={() => requireLogin(action)}>
      いいね
    </button>
  )
}

describe('useRequireLogin', () => {
  it('ログイン中ならそのまま操作する', async () => {
    const action = vi.fn()
    renderWithProviders(<Button action={action} />, { me: user, path: '/posts/p1' })

    await userEvent.click(screen.getByRole('button', { name: 'いいね' }))

    expect(action).toHaveBeenCalledOnce()
    expect(screen.getByTestId('location')).toHaveTextContent('/posts/p1')
  })

  it('未ログインならログイン画面へ移り、ログイン後に戻る先として今の画面を渡す', async () => {
    const action = vi.fn()
    renderWithProviders(<Button action={action} />, { path: '/posts/p1' })

    await userEvent.click(screen.getByRole('button', { name: 'いいね' }))

    expect(action).not.toHaveBeenCalled()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
    expect(screen.getByTestId('location')).toHaveAttribute('data-from', '/posts/p1')
  })
})
