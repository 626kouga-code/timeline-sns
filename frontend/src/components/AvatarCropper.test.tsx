import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { deferred } from '../test/utils'
import { AVATAR_SIZE, AvatarCropper } from './AvatarCropper'
import { cropToSquare } from './cropImage'

// jsdom では画像の描画も canvas も動かないので、切り抜き部品は「範囲を決める」ボタンで範囲を知らせる代役にする
vi.mock('react-easy-crop', () => ({
  default: ({
    image,
    zoom,
    onCropComplete,
  }: {
    image: string
    zoom: number
    onCropComplete: (area: unknown, pixels: unknown) => void
  }) => (
    <div data-testid="cropper" data-image={image} data-zoom={zoom}>
      <button type="button" onClick={() => onCropComplete({}, { x: 10, y: 20, width: 300, height: 300 })}>
        範囲を決める
      </button>
    </div>
  ),
}))
vi.mock('./cropImage', () => ({ cropToSquare: vi.fn() }))

const file = new File(['png-bytes'], 'icon.png', { type: 'image/png' })
const cropped = new Blob(['cropped'], { type: 'image/png' })

function setup(onApply = vi.fn(() => Promise.resolve())) {
  const onClose = vi.fn()
  render(<AvatarCropper file={file} onApply={onApply} onClose={onClose} />)
  return { onApply, onClose, apply: () => screen.getByRole('button', { name: '適用' }) }
}

describe('AvatarCropper', () => {
  beforeEach(() => {
    vi.mocked(cropToSquare).mockReset().mockResolvedValue(cropped)
  })

  it('選んだファイルを読み込んで切り抜き部品に渡す', async () => {
    setup()

    expect(await screen.findByTestId('cropper')).toHaveAttribute('data-image', expect.stringMatching(/^data:image\/png;base64,/))
    expect(screen.getByRole('dialog', { name: 'アイコン画像を切り抜く' })).toBeInTheDocument()
  })

  it('範囲が決まるまで「適用」は押せない', async () => {
    const { apply } = setup()
    await screen.findByTestId('cropper')

    expect(apply()).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '範囲を決める' }))
    expect(apply()).toBeEnabled()
  })

  it('決めた範囲を 400px の正方形に切り抜いて渡す', async () => {
    const { onApply, apply } = setup()
    await userEvent.click(await screen.findByRole('button', { name: '範囲を決める' }))

    await userEvent.click(apply())

    expect(cropToSquare).toHaveBeenCalledWith(
      expect.stringMatching(/^data:/),
      { x: 10, y: 20, width: 300, height: 300 },
      AVATAR_SIZE,
    )
    expect(AVATAR_SIZE).toBe(400)
    expect(onApply).toHaveBeenCalledWith(cropped)
  })

  it('保存中は「保存中…」にして、キャンセル・Esc で閉じない', async () => {
    const saving = deferred<void>()
    const { onClose, apply } = setup(vi.fn(() => saving.promise))
    await userEvent.click(await screen.findByRole('button', { name: '範囲を決める' }))

    await userEvent.click(apply())

    expect(screen.getByRole('button', { name: '保存中…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'キャンセル' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    expect(onClose).not.toHaveBeenCalled()
    saving.resolve()
  })

  it('保存に失敗したら理由を出し、もう一度押せるように戻す', async () => {
    const { apply } = setup(vi.fn(() => Promise.reject(new Error('画像は 5MB 以内にしてください'))))
    await userEvent.click(await screen.findByRole('button', { name: '範囲を決める' }))

    await userEvent.click(apply())

    expect(await screen.findByRole('alert')).toHaveTextContent('画像は 5MB 以内にしてください')
    expect(apply()).toBeEnabled()
  })

  it('切り抜きに失敗したら理由を出す', async () => {
    vi.mocked(cropToSquare).mockRejectedValue(new Error('画像を処理できませんでした'))
    const { onApply, apply } = setup()
    await userEvent.click(await screen.findByRole('button', { name: '範囲を決める' }))

    await userEvent.click(apply())

    expect(await screen.findByRole('alert')).toHaveTextContent('画像を処理できませんでした')
    expect(onApply).not.toHaveBeenCalled()
  })

  it('スライダーで拡大率を変えられる', async () => {
    setup()
    const slider = await screen.findByRole('slider', { name: '拡大' })

    // range 入力は userEvent で動かせないので、値を直接変える
    fireEvent.change(slider, { target: { value: '2.5' } })

    expect(screen.getByTestId('cropper')).toHaveAttribute('data-zoom', '2.5')
  })

  it('キャンセルで閉じる', async () => {
    const { onClose } = setup()

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(onClose).toHaveBeenCalledOnce()
  })
})
