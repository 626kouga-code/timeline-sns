import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// 開発サーバー（npm run dev）と本番ビルドの確認用サーバー（npm run preview。E2E テストで使う）で共通の設定
const serverOptions = {
  // ポートは固定。埋まっていても別ポートには移らない（.claude/skills/dev-server-ports）
  port: 5173,
  strictPort: true,
  // API は同一オリジンに見せる。リフレッシュトークンの Cookie（SameSite=Strict）を送るため
  proxy: {
    '/api': 'http://localhost:8080',
  },
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: serverOptions,
  preview: serverOptions,
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
