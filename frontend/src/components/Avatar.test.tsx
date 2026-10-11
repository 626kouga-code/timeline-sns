import { fireEvent, render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Avatar } from './Avatar'

describe('Avatar', () => {
  it('アイコン画像があれば画像を出す（読み上げはしない）', () => {
    const { container } = render(<Avatar user={{ id: 'u1', displayName: '太郎', avatarUrl: '/api/media/a.jpg' }} />)

    const img = container.querySelector('img')
    expect(img).toHaveAttribute('src', '/api/media/a.jpg')
    expect(img).toHaveAttribute('aria-hidden', 'true')
  })

  it('画像がなければ表示名の頭文字を出す', () => {
    const { container } = render(<Avatar user={{ id: 'u1', displayName: '太郎', avatarUrl: null }} />)

    expect(container.querySelector('img')).toBeNull()
    expect(container).toHaveTextContent('太')
  })

  it('頭文字は絵文字も 1 文字として扱う', () => {
    const { container } = render(<Avatar user={{ id: 'u1', displayName: '😀たろう' }} />)

    expect(container.textContent).toBe('😀')
  })

  it('画像を読み込めなければ頭文字に切り替え、別の URL になれば再び画像を試す', () => {
    const { container, rerender } = render(
      <Avatar user={{ id: 'u1', displayName: '太郎', avatarUrl: '/api/media/broken.jpg' }} />,
    )

    fireEvent.error(container.querySelector('img')!)
    expect(container.querySelector('img')).toBeNull()
    expect(container).toHaveTextContent('太')

    rerender(<Avatar user={{ id: 'u1', displayName: '太郎', avatarUrl: '/api/media/new.jpg' }} />)
    expect(container.querySelector('img')).toHaveAttribute('src', '/api/media/new.jpg')
  })

  it('頭文字の背景色はユーザーごとに決まっている', () => {
    const color = (id: string) =>
      (render(<Avatar user={{ id, displayName: 'A' }} />).container.firstElementChild as HTMLElement).style
        .backgroundColor

    expect(color('user-1')).toBe(color('user-1'))
    expect(color('user-1')).not.toBe('')
  })

  it('大きさを指定できる', () => {
    const { container } = render(<Avatar user={{ id: 'u1', displayName: '太郎' }} size="lg" />)

    expect(container.firstElementChild).toHaveClass('size-24')
  })
})
