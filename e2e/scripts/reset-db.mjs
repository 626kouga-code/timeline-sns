// E2E 用のデータベース timeline_e2e を作り直す（毎回まっさらな状態から始めるため。docs/08）。
// 先に PostgreSQL・S3Mock を起動しておく（起動済みなら何もしない）。
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const repoRoot = fileURLToPath(new URL('../..', import.meta.url))
const run = (args) => execFileSync('docker', args, { cwd: repoRoot, stdio: 'inherit' })

run(['compose', 'up', '-d', '--wait', 'postgres', 's3mock'])
for (const sql of ['DROP DATABASE IF EXISTS timeline_e2e WITH (FORCE)', 'CREATE DATABASE timeline_e2e']) {
  run(['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'timeline', '-d', 'timeline', '-v', 'ON_ERROR_STOP=1', '-c', sql])
}
console.log('timeline_e2e を作り直しました')
