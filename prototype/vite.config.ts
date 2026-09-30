import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 本番フロントエンド(frontend/)の 5173 と衝突しないよう 5174 に固定する。
// 埋まっていても別ポートへ逃がさない（.claude/skills/dev-server-ports の方針）。
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5174,
    strictPort: true,
  },
})
