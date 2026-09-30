import { useState } from 'react'
import { useStore } from '../mock/store'
import { REPORT_REASON_LABELS, type ReportReason, type ReportTargetType } from '../mock/types'
import { Modal } from './Modal'

const TARGET_LABELS: Record<ReportTargetType, string> = { POST: '投稿', COMMENT: 'コメント', USER: 'ユーザー' }

interface Props {
  targetType: ReportTargetType
  targetId: string
  onClose: () => void
}

// 理由を選んで通報する（F-61）
export function ReportDialog({ targetType, targetId, onClose }: Props) {
  const { report, showToast } = useStore()
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [detail, setDetail] = useState('')

  return (
    <Modal title={`${TARGET_LABELS[targetType]}を通報`} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (!reason) return
          report(targetType, targetId, reason, detail.trim())
          showToast('通報しました。ご協力ありがとうございます')
          onClose()
        }}
        className="space-y-4"
      >
        <fieldset className="space-y-2">
          <legend className="mb-2 text-sm font-semibold text-slate-600">通報の理由</legend>
          {(Object.keys(REPORT_REASON_LABELS) as ReportReason[]).map((r) => (
            <label key={r} className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 px-3 py-2.5 has-checked:border-sky-500 has-checked:bg-sky-50">
              <input type="radio" name="reason" value={r} checked={reason === r} onChange={() => setReason(r)} />
              {REPORT_REASON_LABELS[r]}
            </label>
          ))}
        </fieldset>
        <label className="block">
          <span className="text-sm font-semibold text-slate-600">詳細（任意）</span>
          <textarea
            value={detail}
            onChange={(e) => setDetail(e.target.value)}
            rows={3}
            maxLength={500}
            className="mt-1 w-full rounded-lg border border-slate-300 p-2 focus:border-sky-500 focus:outline-none"
          />
        </label>
        <button
          type="submit"
          disabled={!reason}
          className="w-full rounded-full bg-slate-900 py-2.5 font-bold text-white disabled:opacity-40"
        >
          通報する
        </button>
      </form>
    </Modal>
  )
}
