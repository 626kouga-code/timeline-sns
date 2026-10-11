import { describe, expect, it } from 'vitest'
import { formatDateTime, formatRelative } from './time'

const NOW = new Date(2026, 9, 10, 12, 0, 0).getTime()
const ago = (ms: number) => new Date(NOW - ms).toISOString()
const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

describe('formatRelative', () => {
  it.each([
    [0, 'たった今'],
    [59_999, 'たった今'],
    [MINUTE, '1分'],
    [59 * MINUTE, '59分'],
    [HOUR, '1時間'],
    [23 * HOUR + 59 * MINUTE, '23時間'],
    [DAY, '1日'],
    [6 * DAY + 23 * HOUR, '6日'],
  ])('%d ミリ秒前は「%s」', (ms, expected) => {
    expect(formatRelative(ago(ms), NOW)).toBe(expected)
  })

  it('1 週間以上前は月日で表す', () => {
    expect(formatRelative(ago(7 * DAY), NOW)).toBe('10月3日')
    expect(formatRelative(new Date(2025, 11, 31, 9, 0).toISOString(), NOW)).toBe('12月31日')
  })

  it('端末の時計が少しずれて未来の時刻でも「たった今」にする', () => {
    expect(formatRelative(ago(-30_000), NOW)).toBe('たった今')
  })
})

describe('formatDateTime', () => {
  it('年/月/日 時:分 で、時・分は 2 桁にそろえる', () => {
    expect(formatDateTime(new Date(2026, 9, 4, 22, 5).toISOString())).toBe('2026/10/4 22:05')
    expect(formatDateTime(new Date(2026, 0, 9, 3, 7).toISOString())).toBe('2026/1/9 03:07')
  })
})
