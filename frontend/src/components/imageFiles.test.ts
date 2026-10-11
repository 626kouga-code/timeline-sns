import { describe, expect, it } from 'vitest'
import { imageFileError, MAX_IMAGE_BYTES } from './imageFiles'

const file = (type: string, size = 10) => new File([new Uint8Array(size)], 'x', { type })

describe('imageFileError', () => {
  it.each(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])('%s は選べる', (type) => {
    expect(imageFileError(file(type))).toBeNull()
  })

  it.each(['image/bmp', 'image/svg+xml', 'application/pdf', ''])('%s は選べない', (type) => {
    expect(imageFileError(file(type))).toBe('JPEG・PNG・WebP・GIF の画像を選んでください')
  })

  it('5MB ちょうどまでは選べ、超えると選べない', () => {
    expect(imageFileError(file('image/png', MAX_IMAGE_BYTES))).toBeNull()
    expect(imageFileError(file('image/png', MAX_IMAGE_BYTES + 1))).toBe('画像は 5MB 以内にしてください')
  })
})
