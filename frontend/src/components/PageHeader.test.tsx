import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../test/utils'
import { PageHeader, Tabs } from './PageHeader'

describe('PageHeader', () => {
  it('見出しと補足を出し、戻るボタンは指定したときだけ出す', () => {
    renderWithProviders(<PageHeader title="投稿" subtitle="3 件の投稿" />)

    expect(screen.getByRole('heading', { level: 1, name: '投稿' })).toBeInTheDocument()
    expect(screen.getByText('3 件の投稿')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '戻る' })).not.toBeInTheDocument()
  })

  it('戻るボタンで前の画面に戻る', async () => {
    renderWithProviders(<PageHeader title="投稿" back />, { path: ['/home', '/posts/p1'] })

    await userEvent.click(screen.getByRole('button', { name: '戻る' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/home')
  })

  it('子要素（タブなど）を見出しの下に出す', () => {
    renderWithProviders(
      <PageHeader title="プロフィール">
        <p>タブ</p>
      </PageHeader>,
    )

    expect(screen.getByText('タブ')).toBeInTheDocument()
  })
})

describe('Tabs', () => {
  it('選ばれているタブを aria-pressed で示し、押すと onClick を呼ぶ', async () => {
    const onPosts = vi.fn()
    const onLikes = vi.fn()
    render(
      <Tabs
        tabs={[
          { label: '投稿', active: true, onClick: onPosts },
          { label: 'いいね', active: false, onClick: onLikes },
        ]}
      />,
    )

    expect(screen.getByRole('button', { name: '投稿' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'いいね' })).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(screen.getByRole('button', { name: 'いいね' }))

    expect(onLikes).toHaveBeenCalledOnce()
    expect(onPosts).not.toHaveBeenCalled()
  })
})
