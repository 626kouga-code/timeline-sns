import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Modal } from './Modal'

describe('Modal', () => {
  it('タイトルを名前に持つダイアログとして中身を出す', () => {
    render(
      <Modal title="投稿する" onClose={vi.fn()}>
        <p>中身</p>
      </Modal>,
    )

    const dialog = screen.getByRole('dialog', { name: '投稿する' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveTextContent('中身')
  })

  it('閉じるボタン・Esc キー・外側のクリックで閉じる', async () => {
    const onClose = vi.fn()
    render(
      <Modal title="投稿する" onClose={onClose}>
        <p>中身</p>
      </Modal>,
    )

    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))
    await userEvent.keyboard('{Escape}')
    await userEvent.click(screen.getByRole('presentation'))

    expect(onClose).toHaveBeenCalledTimes(3)
  })

  it('ダイアログの中をクリックしても閉じない', async () => {
    const onClose = vi.fn()
    render(
      <Modal title="投稿する" onClose={onClose}>
        <p>中身</p>
      </Modal>,
    )

    await userEvent.click(screen.getByText('中身'))
    await userEvent.keyboard('a')

    expect(onClose).not.toHaveBeenCalled()
  })

  it('閉じたあとは Esc キーを受け付けない', async () => {
    const onClose = vi.fn()
    const { unmount } = render(
      <Modal title="投稿する" onClose={onClose}>
        <p>中身</p>
      </Modal>,
    )

    unmount()
    await userEvent.keyboard('{Escape}')

    expect(onClose).not.toHaveBeenCalled()
  })
})
