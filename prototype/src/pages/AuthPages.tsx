import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { DEMO_PASSWORD } from '../mock/data'
import { useStore } from '../mock/store'

const inputClass =
  'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 focus:border-sky-500 focus:ring-2 focus:ring-sky-100 focus:outline-none'

function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mx-auto max-w-sm px-4 py-10">
      <div className="mb-6 flex size-12 items-center justify-center rounded-2xl bg-sky-500 text-2xl font-black text-white">t</div>
      <h1 className="mb-6 text-2xl font-black">{title}</h1>
      {children}
    </div>
  )
}

function GoogleButton({ label }: { label: string }) {
  const { showToast } = useStore()
  return (
    <button
      type="button"
      onClick={() => showToast('Google ログイン（F-04）はプロトタイプでは未対応です')}
      className="flex w-full items-center justify-center gap-2 rounded-full border border-slate-300 py-2.5 font-semibold hover:bg-slate-50"
    >
      <span className="text-lg font-black text-sky-600">G</span>
      {label}
    </button>
  )
}

function Divider() {
  return (
    <div className="my-5 flex items-center gap-3 text-sm text-slate-400">
      <span className="h-px flex-1 bg-slate-200" />
      または
      <span className="h-px flex-1 bg-slate-200" />
    </div>
  )
}

// ログイン（F-03, `/login`）
export function LoginPage() {
  const { db, login, loginAs } = useStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const from = (location.state as { from?: string } | null)?.from ?? '/home'

  const done = (result: { ok: true } | { ok: false; error: string }) => {
    if (result.ok) navigate(from, { replace: true })
    else setError(result.error)
  }

  return (
    <AuthCard title="ログイン">
      <GoogleButton label="Google でログイン" />
      <Divider />
      <form
        onSubmit={(e) => {
          e.preventDefault()
          done(login(email, password))
        }}
        className="space-y-4"
      >
        <label className="block text-sm font-semibold">
          メールアドレス
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
        </label>
        <label className="block text-sm font-semibold">
          パスワード
          <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} className={inputClass} />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" className="w-full rounded-full bg-slate-900 py-2.5 font-bold text-white hover:bg-slate-700">
          ログイン
        </button>
      </form>
      <div className="mt-4 flex justify-between text-sm">
        <Link to="/password-reset" className="text-sky-600 hover:underline">
          パスワードをお忘れですか？
        </Link>
        <Link to="/signup" className="text-sky-600 hover:underline">
          新規登録
        </Link>
      </div>

      <section className="mt-8 rounded-2xl border border-amber-200 bg-amber-50 p-4">
        <h2 className="text-sm font-bold text-amber-900">デモアカウント</h2>
        <p className="mt-1 text-xs text-amber-800">
          パスワードは全員 <code className="rounded bg-white px-1">{DEMO_PASSWORD}</code>。クリックでそのままログインできます。
        </p>
        <ul className="mt-2 space-y-1">
          {db.users.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                onClick={() => done(loginAs(u.id))}
                className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm hover:bg-amber-100"
              >
                <Avatar user={u} size="sm" />
                <span className="min-w-0 flex-1 truncate">{u.email}</span>
                {u.role === 'ADMIN' && <span className="rounded bg-slate-900 px-1 text-[10px] text-white">管理者</span>}
                {u.status === 'SUSPENDED' && <span className="rounded bg-red-600 px-1 text-[10px] text-white">凍結</span>}
              </button>
            </li>
          ))}
        </ul>
      </section>
    </AuthCard>
  )
}

const HANDLE_PATTERN = /^[A-Za-z0-9_]{3,15}$/

// 新規登録（F-01, `/signup`）
export function SignupPage() {
  const { signup, showToast } = useStore()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '', handle: '', displayName: '' })
  const [agreed, setAgreed] = useState(false)
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const errors = {
    email: /^\S+@\S+\.\S+$/.test(form.email) ? null : '正しいメールアドレスを入力してください',
    password: form.password.length >= 8 ? null : 'パスワードは 8 文字以上で入力してください',
    handle: HANDLE_PATTERN.test(form.handle) ? null : '英数字と _ で 3〜15 文字にしてください',
    displayName:
      form.displayName.trim().length === 0
        ? '表示名を入力してください'
        : [...form.displayName].length > 50
          ? '表示名は 50 文字以内にしてください'
          : null,
    agreed: agreed ? null : '利用規約とプライバシーポリシーへの同意が必要です',
  }
  const hasError = Object.values(errors).some(Boolean)

  const field = (key: keyof typeof form, label: string, type: string, hint?: string, prefix?: string) => (
    <label className="block text-sm font-semibold">
      {label}
      <div className="relative">
        {prefix && <span className="absolute top-1/2 left-3 mt-0.5 -translate-y-1/2 text-slate-400">{prefix}</span>}
        <input
          type={type}
          value={form[key]}
          onChange={(e) => setForm({ ...form, [key]: e.target.value })}
          className={`${inputClass} ${prefix ? 'pl-7' : ''}`}
        />
      </div>
      {touched && errors[key] ? (
        <span className="mt-1 block text-xs font-normal text-red-600">{errors[key]}</span>
      ) : (
        hint && <span className="mt-1 block text-xs font-normal text-slate-500">{hint}</span>
      )}
    </label>
  )

  return (
    <AuthCard title="アカウントを作成">
      <GoogleButton label="Google で登録" />
      <Divider />
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          setTouched(true)
          if (hasError) return
          const result = signup({ ...form, displayName: form.displayName.trim() })
          if (!result.ok) {
            setError(result.error)
            return
          }
          // 本来は確認メールを送り、確認が済むまで投稿などはできない（F-02）
          showToast('登録しました（本来はここで確認メールが届きます）')
          navigate('/home')
        }}
        className="space-y-4"
      >
        {field('email', 'メールアドレス', 'email')}
        {field('password', 'パスワード', 'password', '8 文字以上')}
        {field('handle', 'ユーザーID', 'text', '英数字と _、3〜15 文字。あとから変更できません', '@')}
        {field('displayName', '表示名', 'text', '50 文字以内')}
        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-1" />
          <span>
            <a href="#" className="text-sky-600 hover:underline">利用規約</a>と
            <a href="#" className="text-sky-600 hover:underline">プライバシーポリシー</a>に同意します
          </span>
        </label>
        {touched && errors.agreed && <p className="text-xs text-red-600">{errors.agreed}</p>}
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" className="w-full rounded-full bg-slate-900 py-2.5 font-bold text-white hover:bg-slate-700">
          登録する
        </button>
      </form>
      <p className="mt-4 text-center text-sm">
        アカウントをお持ちの方は{' '}
        <Link to="/login" className="text-sky-600 hover:underline">
          ログイン
        </Link>
      </p>
    </AuthCard>
  )
}
