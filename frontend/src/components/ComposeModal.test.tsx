import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { timelineKeys } from '../api/timeline'
import { deferred, json, mockApi, post, renderWithProviders, request, user } from '../test/utils'
import { ComposeModal } from './ComposeModal'

const png = (name = 'a.png', bytes = 10) => new File([new Uint8Array(bytes)], name, { type: 'image/png' })

function setup() {
  const onClose = vi.fn()
  const result = renderWithProviders(<ComposeModal onClose={onClose} />, { me: user })
  const textbox = screen.getByRole('textbox', { name: '本文' })
  const submit = screen.getByRole('button', { name: '投稿する' })
  const remaining = screen.getByLabelText('残り文字数')
  const input = screen.getByTestId('compose-images')
  return { ...result, onClose, textbox, submit, remaining, input }
}

describe('ComposeModal', () => {
  it('未ログインなら何も出さない', () => {
    renderWithProviders(<ComposeModal onClose={vi.fn()} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('残り文字数を数え、絵文字も 1 文字として数える', async () => {
    const { textbox, remaining } = setup()
    expect(remaining).toHaveTextContent('280')

    await userEvent.type(textbox, 'こんにちは😀')

    expect(remaining).toHaveTextContent('274')
  })

  it('空・空白だけ・280 文字を超えると投稿できない', async () => {
    const { textbox, submit, remaining } = setup()
    expect(submit).toBeDisabled()

    await userEvent.type(textbox, '   ')
    expect(submit).toBeDisabled()

    await userEvent.clear(textbox)
    await userEvent.click(textbox)
    await userEvent.paste('あ'.repeat(280))
    expect(submit).toBeEnabled()
    expect(remaining).toHaveTextContent('0')

    await userEvent.type(textbox, 'い')
    expect(submit).toBeDisabled()
    expect(remaining).toHaveTextContent('-1')
  })

  it('投稿したらタイムラインを取り直し、トーストを出して閉じる', async () => {
    const fetchMock = mockApi({ '/api/posts': () => json(201, post('p1', 'こんにちは')) })
    const { textbox, submit, onClose, queryClient } = setup()
    queryClient.setQueryData(timelineKeys.list('global', user.id), { pages: [], pageParams: [] })

    await userEvent.type(textbox, 'こんにちは')
    await userEvent.click(submit)

    expect(await screen.findByRole('status')).toHaveTextContent('投稿しました')
    expect(onClose).toHaveBeenCalledOnce()
    const form = request(fetchMock).body as FormData
    expect(form.get('body')).toBe('こんにちは')
    expect(queryClient.getQueryState(timelineKeys.list('global', user.id))?.isInvalidated).toBe(true)
  })

  it('送信中は「投稿中…」にして二重に送らない', async () => {
    const response = deferred<Response>()
    const fetchMock = mockApi({ '/api/posts': () => response.promise })
    const { textbox, submit } = setup()

    await userEvent.type(textbox, 'こんにちは')
    await userEvent.click(submit)

    const pending = screen.getByRole('button', { name: '投稿中…' })
    expect(pending).toBeDisabled()
    await userEvent.click(pending)
    expect(fetchMock).toHaveBeenCalledOnce()
    response.resolve(json(201, post('p1', 'こんにちは')))
  })

  it('サーバーの入力エラーを出し、閉じない', async () => {
    mockApi({
      '/api/posts': () =>
        json(400, { code: 'VALIDATION_FAILED', detail: '入力内容に誤りがあります', errors: { images: '画像は 4 枚までです' } }),
    })
    const { textbox, submit, onClose } = setup()

    await userEvent.type(textbox, 'こんにちは')
    await userEvent.click(submit)

    expect(await screen.findByRole('alert')).toHaveTextContent('画像は 4 枚までです')
    expect(onClose).not.toHaveBeenCalled()
  })

  describe('画像', () => {
    it('選んだ画像をプレビューし、取り除ける', async () => {
      const { input } = setup()

      await userEvent.upload(input, [png('a.png'), png('b.png')])

      const list = screen.getByRole('list', { name: '添付する画像' })
      expect(within(list).getAllByRole('img')).toHaveLength(2)

      await userEvent.click(screen.getByRole('button', { name: '画像 1 を取り除く' }))
      expect(within(list).getAllByRole('img')).toHaveLength(1)
      expect(within(list).getByRole('img')).toHaveAccessibleName('添付する画像 1')
    })

    it('画像だけなら本文が空でも投稿でき、画像を images パートで送る', async () => {
      const fetchMock = mockApi({ '/api/posts': () => json(201, post('p1', '')) })
      const { input, submit } = setup()

      await userEvent.upload(input, [png('a.png'), png('b.png')])
      await userEvent.click(submit)

      await screen.findByRole('status')
      const form = request(fetchMock).body as FormData
      expect(form.get('body')).toBe('')
      expect(form.getAll('images').map((f) => (f as File).name)).toEqual(['a.png', 'b.png'])
    })

    it('4 枚を超える分は追加せず、追加ボタンも押せなくする', async () => {
      const { input } = setup()

      await userEvent.upload(input, [png('1.png'), png('2.png'), png('3.png'), png('4.png'), png('5.png')])

      expect(within(screen.getByRole('list', { name: '添付する画像' })).getAllByRole('img')).toHaveLength(4)
      expect(screen.getByRole('alert')).toHaveTextContent('画像は 4 枚までです')
      expect(screen.getByRole('button', { name: '画像を追加' })).toBeDisabled()
    })

    it('対応していない形式・5MB を超える画像は追加しない', async () => {
      const { input } = setup()
      const event = userEvent.setup({ applyAccept: false })

      await event.upload(input, new File(['x'], 'memo.txt', { type: 'text/plain' }))
      expect(screen.getByRole('alert')).toHaveTextContent('JPEG・PNG・WebP・GIF の画像を選んでください')

      await event.upload(input, png('big.png', 5 * 1024 * 1024 + 1))
      expect(screen.getByRole('alert')).toHaveTextContent('画像は 5MB 以内にしてください')
      expect(screen.queryByRole('list', { name: '添付する画像' })).not.toBeInTheDocument()
    })
  })
})
