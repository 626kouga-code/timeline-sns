import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setAccessToken } from '../api/client'

// jsdom は scrollTo を実装していない
window.scrollTo = () => {}

// 複数のテストファイルを並行して動かすと、最初の描画が既定の 1 秒に間に合わないことがある
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
  setAccessToken(null)
  vi.unstubAllGlobals()
})
