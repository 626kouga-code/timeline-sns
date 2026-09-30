import { useState } from 'react'
import { Link } from 'react-router'
import { Avatar } from '../components/Avatar'
import { PageHeader, Tabs } from '../components/PageHeader'
import { findUser, formatDateTime } from '../mock/selectors'
import { useStore } from '../mock/store'
import { REPORT_REASON_LABELS, REPORT_STATUS_LABELS, type Report, type ReportStatus, type User } from '../mock/types'

const STATUS_STYLE: Record<ReportStatus, string> = {
  OPEN: 'bg-amber-100 text-amber-800',
  RESOLVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-slate-200 text-slate-700',
}
const TARGET_LABELS = { POST: '投稿', COMMENT: 'コメント', USER: 'ユーザー' } as const

// 管理画面（F-62, `/admin/reports`）
export function AdminReportsPage() {
  const { db } = useStore()
  const [filter, setFilter] = useState<ReportStatus | 'ALL'>('OPEN')
  const list = db.reports
    .filter((r) => filter === 'ALL' || r.status === filter)
    .sort((a, b) => b.createdAt - a.createdAt)
  const count = (s: ReportStatus) => db.reports.filter((r) => r.status === s).length

  return (
    <>
      <PageHeader title="通報一覧" subtitle="管理者のみ">
        <Tabs
          tabs={[
            ...(['OPEN', 'RESOLVED', 'REJECTED'] as const).map((s) => ({
              label: `${REPORT_STATUS_LABELS[s]} (${count(s)})`,
              active: filter === s,
              onClick: () => setFilter(s),
            })),
            { label: 'すべて', active: filter === 'ALL', onClick: () => setFilter('ALL') },
          ]}
        />
      </PageHeader>
      {list.length === 0 && <p className="px-4 py-16 text-center text-slate-500">該当する通報はありません</p>}
      {list.map((r) => (
        <ReportCard key={r.id} report={r} />
      ))}
    </>
  )
}

function ReportCard({ report }: { report: Report }) {
  const { db, setReportStatus, adminDeletePost, adminDeleteComment, setSuspended, showToast } = useStore()
  const reporter = findUser(db, report.reporterId)

  const post = report.targetType === 'POST' ? db.posts.find((p) => p.id === report.targetId) : undefined
  const comment = report.targetType === 'COMMENT' ? db.comments.find((c) => c.id === report.targetId) : undefined
  const targetUser: User | undefined =
    report.targetType === 'USER'
      ? findUser(db, report.targetId)
      : post
        ? findUser(db, post.userId)
        : comment
          ? findUser(db, comment.userId)
          : undefined
  const targetGone = (report.targetType === 'POST' && !post) || (report.targetType === 'COMMENT' && (!comment || !!comment.deletedAt))

  const resolve = (message: string) => {
    setReportStatus(report.id, 'RESOLVED')
    showToast(message)
  }

  const btn = 'rounded-full border px-3 py-1 text-sm font-semibold'

  return (
    <div className="space-y-3 border-b border-slate-100 px-4 py-4">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className={`rounded-full px-2 py-0.5 text-xs font-bold ${STATUS_STYLE[report.status]}`}>
          {REPORT_STATUS_LABELS[report.status]}
        </span>
        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs">{TARGET_LABELS[report.targetType]}</span>
        <span className="font-semibold">{REPORT_REASON_LABELS[report.reason]}</span>
        <span className="ml-auto text-xs text-slate-500">{formatDateTime(report.createdAt)}</span>
      </div>
      <p className="text-sm text-slate-600">
        通報者: {reporter ? <Link to={`/users/${reporter.handle}`} className="text-sky-600 hover:underline">@{reporter.handle}</Link> : '削除されたユーザー'}
        {report.detail && <span className="mt-1 block rounded bg-slate-50 px-2 py-1">「{report.detail}」</span>}
      </p>

      <div className="rounded-xl border border-slate-200 p-3">
        {targetUser && (
          <div className="mb-2 flex items-center gap-2 text-sm">
            <Avatar user={targetUser} size="sm" />
            <span className="font-bold">{targetUser.displayName}</span>
            <span className="text-slate-500">@{targetUser.handle}</span>
            {targetUser.status === 'SUSPENDED' && (
              <span className="rounded bg-red-600 px-1.5 text-xs font-bold text-white">凍結中</span>
            )}
          </div>
        )}
        {report.targetType === 'POST' &&
          (post ? (
            <Link to={`/posts/${post.id}`} className="block text-sm hover:underline">
              {post.body || '（画像のみの投稿）'}
              {post.images.length > 0 && <span className="text-slate-500">［画像 {post.images.length} 枚］</span>}
            </Link>
          ) : (
            <p className="text-sm text-slate-500">この投稿は削除済みです</p>
          ))}
        {report.targetType === 'COMMENT' &&
          (comment && !comment.deletedAt ? (
            <Link to={`/posts/${comment.postId}`} className="block text-sm hover:underline">
              {comment.body}
            </Link>
          ) : (
            <p className="text-sm text-slate-500">このコメントは削除済みです</p>
          ))}
        {report.targetType === 'USER' && targetUser?.bio && <p className="text-sm text-slate-600">{targetUser.bio}</p>}
      </div>

      <div className="flex flex-wrap gap-2">
        {post && (
          <button
            type="button"
            className={`${btn} border-red-200 text-red-600 hover:bg-red-50`}
            onClick={() => {
              if (!window.confirm('この投稿を削除しますか？')) return
              adminDeletePost(post.id)
              resolve('投稿を削除し、対応済みにしました')
            }}
          >
            投稿を削除
          </button>
        )}
        {comment && !comment.deletedAt && (
          <button
            type="button"
            className={`${btn} border-red-200 text-red-600 hover:bg-red-50`}
            onClick={() => {
              if (!window.confirm('このコメントを削除しますか？')) return
              adminDeleteComment(comment.id)
              resolve('コメントを削除し、対応済みにしました')
            }}
          >
            コメントを削除
          </button>
        )}
        {targetUser && targetUser.role !== 'ADMIN' &&
          (targetUser.status === 'ACTIVE' ? (
            <button
              type="button"
              className={`${btn} border-red-200 text-red-600 hover:bg-red-50`}
              onClick={() => {
                if (!window.confirm(`@${targetUser.handle} を凍結しますか？ログインできなくなり、投稿・プロフィールが非表示になります。`)) return
                setSuspended(targetUser.id, true)
                resolve(`@${targetUser.handle} を凍結し、対応済みにしました`)
              }}
            >
              アカウントを凍結
            </button>
          ) : (
            <button
              type="button"
              className={`${btn} border-slate-300 hover:bg-slate-50`}
              onClick={() => {
                setSuspended(targetUser.id, false)
                showToast(`@${targetUser.handle} の凍結を解除しました`)
              }}
            >
              凍結を解除
            </button>
          ))}
        <span className="ml-auto flex items-center gap-1 text-sm">
          対応状況
          <select
            value={report.status}
            onChange={(e) => setReportStatus(report.id, e.target.value as ReportStatus)}
            className="rounded-lg border border-slate-300 px-2 py-1"
          >
            {(Object.keys(REPORT_STATUS_LABELS) as ReportStatus[]).map((s) => (
              <option key={s} value={s}>
                {REPORT_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </span>
      </div>
      {targetGone && report.status === 'OPEN' && (
        <p className="text-xs text-slate-500">対象は既に削除されています。対応済みまたは却下にしてください。</p>
      )}
    </div>
  )
}
