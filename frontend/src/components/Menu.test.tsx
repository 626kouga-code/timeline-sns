import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Menu } from './Menu'

describe('Menu', () => {
  it('項目がなければ何も出さない', () => {
    const { container } = render(<Menu items={[]} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('ボタンで開閉し、開いているかを aria-expanded で示す', async () => {
    render(<Menu items={[{ label: '削除', icon: 'trash', onSelect: vi.fn() }]} label="投稿のメニュー" />)
    const button = screen.getByRole('button', { name: '投稿のメニュー' })

    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()

    await userEvent.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('button', { name: '削除' })).toBeInTheDocument()

    await userEvent.click(button)
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
  })

  it('項目を選ぶと閉じてから onSelect を呼ぶ', async () => {
    const onSelect = vi.fn()
    render(
      <Menu
        items={[
          { label: '通報', icon: 'flag', onSelect: vi.fn() },
          { label: '削除', icon: 'trash', danger: true, onSelect },
        ]}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(onSelect).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
  })

  it('メニューの外をクリックすると閉じる', async () => {
    render(
      <div>
        <p>外側</p>
        <Menu items={[{ label: '削除', icon: 'trash', onSelect: vi.fn() }]} />
      </div>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByText('外側'))

    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
  })

  it('メニュー内のクリックは親（投稿カードなど）に伝えない', async () => {
    const onParentClick = vi.fn()
    render(
      <div onClick={onParentClick}>
        <Menu items={[{ label: '削除', icon: 'trash', onSelect: vi.fn() }]} />
      </div>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(onParentClick).not.toHaveBeenCalled()
  })
})
