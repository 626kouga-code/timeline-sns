import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { PostImage } from '../api/types'
import { ImageGrid } from './ImageGrid'

const images = (n: number): PostImage[] =>
  Array.from({ length: n }, (_, i) => ({
    url: `/api/media/${i + 1}.jpg`,
    thumbnailUrl: `/api/media/${i + 1}-thumb.jpg`,
    width: 800,
    height: 600,
  }))

const lightbox = () => screen.queryByRole('dialog', { name: '画像の拡大表示' })

describe('ImageGrid', () => {
  it('画像がなければ何も出さない', () => {
    const { container } = render(<ImageGrid images={[]} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('一覧にはサムネイルを並べる', () => {
    const { container } = render(<ImageGrid images={images(3)} />)

    expect(screen.getAllByRole('button', { name: /を拡大$/ })).toHaveLength(3)
    expect([...container.querySelectorAll('img')].map((img) => img.getAttribute('src'))).toEqual([
      '/api/media/1-thumb.jpg',
      '/api/media/2-thumb.jpg',
      '/api/media/3-thumb.jpg',
    ])
  })

  it('1 枚なら 1 列、2 枚以上なら 2 列に並べる', () => {
    const { container, rerender } = render(<ImageGrid images={images(1)} />)
    expect(container.firstElementChild).toHaveClass('grid-cols-1')

    rerender(<ImageGrid images={images(4)} />)
    expect(container.firstElementChild).toHaveClass('grid-cols-2')
  })

  it('押すと表示用の画像を拡大表示し、前後に切り替えられる', async () => {
    render(<ImageGrid images={images(3)} />)

    await userEvent.click(screen.getByRole('button', { name: '画像 2 を拡大' }))

    const dialog = lightbox()!
    expect(within(dialog).getByRole('img', { name: '画像 2 / 3' })).toHaveAttribute('src', '/api/media/2.jpg')

    await userEvent.click(within(dialog).getByRole('button', { name: '次の画像' }))
    expect(within(dialog).getByRole('img', { name: '画像 3 / 3' })).toBeInTheDocument()
    // 最後の画像では「次」を出さない
    expect(within(dialog).queryByRole('button', { name: '次の画像' })).not.toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: '前の画像' }))
    await userEvent.click(within(dialog).getByRole('button', { name: '前の画像' }))
    expect(within(dialog).getByRole('img', { name: '画像 1 / 3' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: '前の画像' })).not.toBeInTheDocument()
  })

  it('矢印キーで切り替え、端では止まる', async () => {
    render(<ImageGrid images={images(2)} />)
    await userEvent.click(screen.getByRole('button', { name: '画像 1 を拡大' }))

    await userEvent.keyboard('{ArrowLeft}')
    expect(screen.getByRole('img', { name: '画像 1 / 2' })).toBeInTheDocument()
    await userEvent.keyboard('{ArrowRight}')
    await userEvent.keyboard('{ArrowRight}')
    expect(screen.getByRole('img', { name: '画像 2 / 2' })).toBeInTheDocument()
  })

  it('Esc・閉じるボタン・背景のクリックで閉じる。画像のクリックでは閉じない', async () => {
    render(<ImageGrid images={images(1)} />)
    const open = () => userEvent.click(screen.getByRole('button', { name: '画像 1 を拡大' }))

    await open()
    await userEvent.click(screen.getByRole('img', { name: '画像 1 / 1' }))
    expect(lightbox()).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')
    expect(lightbox()).not.toBeInTheDocument()

    await open()
    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))
    expect(lightbox()).not.toBeInTheDocument()

    await open()
    await userEvent.click(lightbox()!)
    expect(lightbox()).not.toBeInTheDocument()
  })

  it('画像を押しても親（投稿カード）のクリックにはならない', async () => {
    const onParentClick = vi.fn()
    render(
      <div onClick={onParentClick}>
        <ImageGrid images={images(1)} />
      </div>,
    )

    await userEvent.click(screen.getByRole('button', { name: '画像 1 を拡大' }))
    await userEvent.keyboard('{Escape}')
    await userEvent.click(screen.getByRole('button', { name: '画像 1 を拡大' }))
    await userEvent.click(lightbox()!)

    expect(onParentClick).not.toHaveBeenCalled()
  })
})
