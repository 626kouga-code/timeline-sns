import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'

// まだ実装していない画面。各機能の Issue で本来の画面に置き換える
export function PlaceholderPage({ title, feature, back = true }: { title: string; feature: string; back?: boolean }) {
  return (
    <>
      <PageHeader title={title} back={back} />
      <div className="px-8 py-16 text-center">
        <p className="text-lg font-bold">この画面は準備中です</p>
        <p className="mt-2 text-sm text-slate-500">{feature}</p>
        <Link to="/" className="mt-6 inline-block text-sm text-sky-600 hover:underline">
          トップへ戻る
        </Link>
      </div>
    </>
  )
}
