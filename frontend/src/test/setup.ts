import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setAccessToken } from '../api/client'

// jsdom は scrollTo を実装していない
window.scrollTo = () => {}

// jsdom は URL.createObjectURL（画像のプレビュー）を実装していない
let objectUrls = 0
URL.createObjectURL = () => `blob:test/${++objectUrls}`
URL.revokeObjectURL = () => {}

// 複数のテストファイルを並行して動かすと、最初の描画が既定の 1 秒に間に合わないことがある
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
  setAccessToken(null)
  vi.unstubAllGlobals()
})
