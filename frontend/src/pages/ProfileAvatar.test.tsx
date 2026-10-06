import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { json, mockApi, renderApp, user } from '../test/utils'

// jsdom では画像の読み込みも canvas も動かないので、切り抜き画面は「適用」で決まった画像を返す代役にする
vi.mock('../components/AvatarCropper', () => ({
  AvatarCropper: ({ onApply, onClose }: { onApply: (image: Blob) => Promise<void>; onClose: () => void }) => (
    <div role="dialog" aria-label="アイコン画像を切り抜く">
      <button type="button" onClick={() => void onApply(new Blob(['cropped'], { type: 'image/png' })).catch(() => {})}>
        適用
      </button>
      <button type="button" onClick={onClose}>
        キャンセル
      </button>
    </div>
  ),
}))

const withAvatar = { ...user, avatarUrl: '/api/media/avatars/u1/old.jpg' }
const sessionAs = (me: typeof user) => () => json(200, { accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user: me })
const png = (bytes = 10) => new File([new Uint8Array(bytes)], 'icon.png', { type: 'image/png' })

describe('アイコン画像', () => {
  it('画像を選んで切り抜くとアップロードし、ナビのアイコンも変わる', async () => {
    const fetchMock = mockApi({
      '/api/auth/refresh': sessionAs(user),
      '/api/me/avatar': () => json(200, { ...user, avatarUrl: '/api/media/avatars/u1/new.png' }),
    })
    renderApp('/settings/profile')

    await userEvent.upload(await screen.findByTestId('avatar-input'), png())
    await userEvent.click(await screen.findByRole('button', { name: '適用' }))

    expect(await screen.findByText('アイコン画像を変更しました')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'アイコン画像を切り抜く' })).not.toBeInTheDocument()
    expect(document.querySelectorAll('img[src="/api/media/avatars/u1/new.png"]').length).toBeGreaterThan(0)

    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/me/avatar') ?? []
    expect(init?.method).toBe('PUT')
    expect((init?.body as FormData).get('file')).toBeInstanceOf(Blob)
  })

  it('画像以外や 5MB を超えるファイルは切り抜き画面を開かずにメッセージを出す', async () => {
    mockApi({ '/api/auth/refresh': sessionAs(user) })
    renderApp('/settings/profile')
    const input = await screen.findByTestId('avatar-input')
    // accept 属性をすり抜けたファイル（ドラッグ＆ドロップ・「すべてのファイル」での選択）も画面で弾く
    const event = userEvent.setup({ applyAccept: false })

    await event.upload(input, new File(['x'], 'memo.txt', { type: 'text/plain' }))
    expect(await screen.findByText('JPEG・PNG・WebP・GIF の画像を選んでください')).toBeInTheDocument()

    await event.upload(input, png(5 * 1024 * 1024 + 1))
    expect(await screen.findByText('画像は 5MB 以内にしてください')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'アイコン画像を切り抜く' })).not.toBeInTheDocument()
  })

  it('キャンセルするとアップロードしない', async () => {
    const fetchMock = mockApi({ '/api/auth/refresh': sessionAs(user) })
    renderApp('/settings/profile')

    await userEvent.upload(await screen.findByTestId('avatar-input'), png())
    await userEvent.click(await screen.findByRole('button', { name: 'キャンセル' }))

    expect(screen.queryByRole('dialog', { name: 'アイコン画像を切り抜く' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path === '/api/me/avatar')).toBe(false)
  })

  it('画像を削除すると頭文字のアイコンに戻る', async () => {
    const fetchMock = mockApi({
      '/api/auth/refresh': sessionAs(withAvatar),
      '/api/me/avatar': () => json(200, { ...user, avatarUrl: null }),
    })
    renderApp('/settings/profile')

    await userEvent.click(await screen.findByRole('button', { name: '画像を削除' }))

    expect(await screen.findByText('アイコン画像を削除しました')).toBeInTheDocument()
    expect(document.querySelector('img[src="/api/media/avatars/u1/old.jpg"]')).toBeNull()
    expect(screen.queryByRole('button', { name: '画像を削除' })).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/me/avatar', expect.objectContaining({ method: 'DELETE' }))
  })

  it('画像を読み込めなければ頭文字のアイコンを出す', async () => {
    mockApi({ '/api/auth/refresh': sessionAs(withAvatar) })
    renderApp('/settings/profile')

    await screen.findByRole('button', { name: '画像を削除' })
    const images = document.querySelectorAll('img[src="/api/media/avatars/u1/old.jpg"]')
    expect(images.length).toBeGreaterThan(0)
    images.forEach((img) => fireEvent.error(img))

    expect(document.querySelector('img[src="/api/media/avatars/u1/old.jpg"]')).toBeNull()
    expect(screen.getAllByText('太').length).toBeGreaterThan(0)
  })
})
