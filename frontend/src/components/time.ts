/** タイムライン用の短い相対時刻（「たった今」「5分」「3時間」「2日」、1 週間以上前は日付） */
export function formatRelative(iso: string, now = Date.now()): string {
  const time = new Date(iso).getTime()
  const minutes = Math.floor((now - time) / 60000)
  if (minutes < 1) return 'たった今'
  if (minutes < 60) return `${minutes}分`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}時間`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}日`
  const d = new Date(time)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

/** 投稿詳細用の日時（2026/10/4 22:05） */
export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
