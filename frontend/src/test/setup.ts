import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setAccessToken } from '../api/client'

// jsdom は scrollTo を実装していない
window.scrollTo = () => {}

afterEach(() => {
  cleanup()
  setAccessToken(null)
  vi.unstubAllGlobals()
})
