import { describe, expect, it } from 'vitest'
import { countChars, MAX_BODY } from './text'

describe('countChars', () => {
  it('絵文字なども 1 文字と数える（サーバーの @MaxCodePoints と同じ）', () => {
    expect(countChars('')).toBe(0)
    expect(countChars('abc')).toBe(3)
    expect(countChars('こんにちは')).toBe(5)
    expect('😀'.length).toBe(2)
    expect(countChars('😀😀')).toBe(2)
    expect(countChars('𠮷野家')).toBe(3)
  })

  it('本文の上限は 280 文字', () => {
    expect(MAX_BODY).toBe(280)
  })
})
