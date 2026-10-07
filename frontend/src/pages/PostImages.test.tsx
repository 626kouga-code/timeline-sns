import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Post, PostImage } from '../api/types'
import { json, mockApi, page, post, renderApp, session } from '../test/utils'

const png = (name = 'photo.png', bytes = 10) => new File([new Uint8Array(bytes)], name, { type: 'image/png' })

function image(n: number): PostImage {
  return { url: `/api/media/posts/p1/${n}.jpg`, thumbnailUrl: `/api/media/posts/p1/${n}-thumb.jpg`, width: 800, height: 600 }
}

function withImages(count: number): Post {
  return { ...post('p1', '写真つき'), images: Array.from({ length: count }, (_, i) => image(i + 1)) }
}

async function openCompose() {
  await userEvent.click((await screen.findAllByRole('button', { name: '投稿する' }))[0])
  return screen.getByRole('dialog', { name: '投稿する' })
}

describe('画像投稿', () => {
  it('画像を添付して投稿できる（本文なしでもよい）', async () => {
    const fetchMock = mockApi({
      '/api/auth/refresh': session,
      '/api/posts': () => json(201, withImages(2)),
    })
    renderApp('/home')
    const dialog = await openCompose()

    // 画像がなく本文も空なら投稿できない
    expect(within(dialog).getByRole('button', { name: '投稿する' })).toBeDisabled()
    await userEvent.upload(within(dialog).getByTestId('compose-images'), [png('a.png'), png('b.png')])

    expect(within(dialog).getAllByRole('img', { name: /添付する画像/ })).toHaveLength(2)
    await userEvent.click(within(dialog).getByRole('button', { name: '投稿する' }))

    expect(await screen.findByText('投稿しました')).toBeInTheDocument()
    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/posts') ?? []
    const form = init?.body as FormData
    expect(form.get('body')).toBe('')
    expect(form.getAll('images').map((f) => (f as File).name)).toEqual(['a.png', 'b.png'])
  })

  it('取り除いた画像は送らない', async () => {
    const fetchMock = mockApi({ '/api/auth/refresh': session, '/api/posts': () => json(201, withImages(1)) })
    renderApp('/home')
    const dialog = await openCompose()

    await userEvent.upload(within(dialog).getByTestId('compose-images'), [png('a.png'), png('b.png')])
    await userEvent.click(within(dialog).getByRole('button', { name: '画像 1 を取り除く' }))
    await userEvent.click(within(dialog).getByRole('button', { name: '投稿する' }))

    await screen.findByText('投稿しました')
    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/posts') ?? []
    expect((init?.body as FormData).getAll('images').map((f) => (f as File).name)).toEqual(['b.png'])
  })

  it('5 枚目以降と、画像以外・5MB を超えるファイルは添付しない', async () => {
    mockApi({ '/api/auth/refresh': session })
    renderApp('/home')
    const dialog = await openCompose()
    const input = within(dialog).getByTestId('compose-images')
    // accept 属性をすり抜けたファイルも画面で弾く
    const event = userEvent.setup({ applyAccept: false })

    await event.upload(input, [png('1.png'), png('2.png'), png('3.png'), png('4.png'), png('5.png')])
    expect(within(dialog).getAllByRole('img', { name: /添付する画像/ })).toHaveLength(4)
    expect(within(dialog).getByRole('alert')).toHaveTextContent('画像は 4 枚までです')
    expect(within(dialog).getByRole('button', { name: '画像を追加' })).toBeDisabled()

    await userEvent.click(within(dialog).getByRole('button', { name: '画像 4 を取り除く' }))
    await event.upload(input, new File(['x'], 'memo.txt', { type: 'text/plain' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('JPEG・PNG・WebP・GIF の画像を選んでください')
    await event.upload(input, png('big.png', 5 * 1024 * 1024 + 1))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('画像は 5MB 以内にしてください')
    expect(within(dialog).getAllByRole('img', { name: /添付する画像/ })).toHaveLength(3)
  })

  it('サーバーで弾かれたらメッセージを出し、画面は閉じない', async () => {
    mockApi({
      '/api/auth/refresh': session,
      '/api/posts': () =>
        json(400, { code: 'UNSUPPORTED_IMAGE', detail: 'JPEG・PNG・WebP・GIF の画像を選んでください' }),
    })
    renderApp('/home')
    const dialog = await openCompose()

    await userEvent.upload(within(dialog).getByTestId('compose-images'), png())
    await userEvent.click(within(dialog).getByRole('button', { name: '投稿する' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('JPEG・PNG・WebP・GIF の画像を選んでください')
    expect(screen.getByRole('dialog', { name: '投稿する' })).toBeInTheDocument()
  })
})

describe('投稿の画像の表示', () => {
  it('一覧ではサムネイルを並べ、タップで拡大表示して切り替えられる', async () => {
    mockApi({ '/api/timeline/global': () => page([withImages(3)]) })
    renderApp('/')

    const thumbnails = await screen.findAllByRole('button', { name: /画像 \d を拡大/ })
    expect(thumbnails).toHaveLength(3)
    expect(thumbnails[0].querySelector('img')).toHaveAttribute('src', '/api/media/posts/p1/1-thumb.jpg')

    await userEvent.click(thumbnails[1])
    const lightbox = screen.getByRole('dialog', { name: '画像の拡大表示' })
    expect(within(lightbox).getByRole('img')).toHaveAttribute('src', '/api/media/posts/p1/2.jpg')
    // 拡大表示を開いても、投稿詳細へは移らない
    expect(screen.queryByRole('heading', { name: '投稿' })).not.toBeInTheDocument()

    await userEvent.click(within(lightbox).getByRole('button', { name: '次の画像' }))
    expect(within(lightbox).getByRole('img')).toHaveAttribute('src', '/api/media/posts/p1/3.jpg')
    expect(within(lightbox).queryByRole('button', { name: '次の画像' })).not.toBeInTheDocument()

    await userEvent.keyboard('{ArrowLeft}')
    expect(within(lightbox).getByRole('img')).toHaveAttribute('src', '/api/media/posts/p1/2.jpg')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: '画像の拡大表示' })).not.toBeInTheDocument()
  })

  it('投稿詳細にも画像を出す', async () => {
    mockApi({ '/api/posts/p1': () => json(200, withImages(1)) })
    renderApp('/posts/p1')

    expect(await screen.findByRole('button', { name: '画像 1 を拡大' })).toBeInTheDocument()
  })
})
