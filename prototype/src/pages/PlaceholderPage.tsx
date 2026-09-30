import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'

// プロトタイプの対象外にした画面（メール確認、パスワード再設定、ユーザーID設定、アカウント設定）
export function PlaceholderPage({ title, feature }: { title: string; feature: string }) {
  return (
    <>
      <PageHeader title={title} back />
      <div className="px-8 py-16 text-center">
        <p className="text-lg font-bold">この画面はプロトタイプの対象外です</p>
        <p className="mt-2 text-sm text-slate-500">{feature}</p>
        <Link to="/" className="mt-6 inline-block text-sm text-sky-600 hover:underline">
          全体タイムラインへ戻る
        </Link>
      </div>
    </>
  )
}
