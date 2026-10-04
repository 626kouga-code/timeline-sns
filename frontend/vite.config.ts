import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // ポートは固定。埋まっていても別ポートには移らない（.claude/skills/dev-server-ports）
    port: 5173,
    strictPort: true,
    // API は同一オリジンに見せる。リフレッシュトークンの Cookie（SameSite=Strict）を送るため
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
