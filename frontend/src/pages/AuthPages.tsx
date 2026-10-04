import { useMutation } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { useToast } from '../components/toast'

const inputClass =
  'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 focus:border-sky-500 focus:ring-2 focus:ring-sky-100 focus:outline-none'

const submitClass =
  'w-full rounded-full bg-slate-900 py-2.5 font-bold text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50'

function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mx-auto max-w-sm px-4 py-10">
      <div className="mb-6 flex size-12 items-center justify-center rounded-2xl bg-sky-500 text-2xl font-black text-white">t</div>
      <h1 className="mb-6 text-2xl font-black">{title}</h1>
      {children}
    </div>
  )
}

// Google ログインは F-04（#10）で実装する
function GoogleButton({ label }: { label: string }) {
  const showToast = useToast()
  return (
    <button
      type="button"
      onClick={() => showToast('Google ログインは準備中です')}
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

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : '予期しないエラーが発生しました'
}

// ログイン（F-03, `/login`）。成功後の遷移は App の GuestOnly が行う
export function LoginPage() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const mutation = useMutation({ mutationFn: login })

  return (
    <AuthCard title="ログイン">
      <GoogleButton label="Google でログイン" />
      <Divider />
      <form
        onSubmit={(e) => {
          e.preventDefault()
          mutation.mutate({ email: email.trim(), password })
        }}
        className="space-y-4"
      >
        <label className="block text-sm font-semibold">
          メールアドレス
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={inputClass}
          />
        </label>
        <label className="block text-sm font-semibold">
          パスワード
          <input
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass}
          />
        </label>
        {mutation.isError && (
          <p role="alert" className="text-sm text-red-600">
            {errorMessage(mutation.error)}
          </p>
        )}
        <button type="submit" disabled={mutation.isPending} className={submitClass}>
          {mutation.isPending ? 'ログイン中…' : 'ログイン'}
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
    </AuthCard>
  )
}

// 入力チェックはバックエンド（SignupRequest.java）と同じ条件にする
const HANDLE_PATTERN = /^[A-Za-z0-9_]{3,15}$/

type SignupField = 'email' | 'password' | 'handle' | 'displayName'

function validate(form: Record<SignupField, string>, agreed: boolean) {
  const displayNameLength = [...form.displayName.trim()].length
  return {
    email: /^\S+@\S+\.\S+$/.test(form.email.trim()) ? null : '正しいメールアドレスを入力してください',
    password:
      form.password.length >= 8 && form.password.length <= 72 ? null : 'パスワードは 8〜72 文字で入力してください',
    handle: HANDLE_PATTERN.test(form.handle) ? null : 'ユーザーIDは英数字と _ で 3〜15 文字にしてください',
    displayName:
      displayNameLength === 0
        ? '表示名を入力してください'
        : displayNameLength > 50
          ? '表示名は 50 文字以内にしてください'
          : null,
    agreedToTerms: agreed ? null : '利用規約とプライバシーポリシーへの同意が必要です',
  }
}

// 新規登録（F-01, `/signup`）。成功後の遷移は App の GuestOnly が行う
export function SignupPage() {
  const { signup } = useAuth()
  const showToast = useToast()
  const [form, setForm] = useState<Record<SignupField, string>>({ email: '', password: '', handle: '', displayName: '' })
  const [agreed, setAgreed] = useState(false)
  const [touched, setTouched] = useState(false)
  const mutation = useMutation({
    mutationFn: signup,
    onSuccess: () => showToast('登録しました'),
  })

  const clientErrors = validate(form, agreed)
  const hasClientError = Object.values(clientErrors).some(Boolean)
  // サーバーの項目別エラー（メールアドレスやユーザーIDの重複など）
  const serverErrors = mutation.error instanceof ApiError ? mutation.error.errors : {}
  const errorOf = (key: keyof typeof clientErrors) => (touched ? clientErrors[key] : null) ?? serverErrors[key] ?? null
  const formError = mutation.isError && Object.keys(serverErrors).length === 0 ? errorMessage(mutation.error) : null

  const field = (key: SignupField, label: string, type: string, autoComplete: string, hint?: string, prefix?: string) => {
    const error = errorOf(key)
    const id = `signup-${key}`
    const note = error ?? hint
    return (
      <div className="text-sm">
        <label htmlFor={id} className="block font-semibold">
          {label}
        </label>
        <div className="relative">
          {prefix && <span className="absolute top-1/2 left-3 mt-0.5 -translate-y-1/2 text-slate-400">{prefix}</span>}
          <input
            id={id}
            type={type}
            autoComplete={autoComplete}
            value={form[key]}
            onChange={(e) => setForm({ ...form, [key]: e.target.value })}
            aria-invalid={!!error}
            aria-describedby={note ? `${id}-note` : undefined}
            className={`${inputClass} ${prefix ? 'pl-7' : ''}`}
          />
        </div>
        {note && (
          <span id={`${id}-note`} className={`mt-1 block text-xs ${error ? 'text-red-600' : 'text-slate-500'}`}>
            {note}
          </span>
        )}
      </div>
    )
  }

  return (
    <AuthCard title="アカウントを作成">
      <GoogleButton label="Google で登録" />
      <Divider />
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          setTouched(true)
          if (hasClientError) return
          mutation.mutate({
            email: form.email.trim(),
            password: form.password,
            handle: form.handle,
            displayName: form.displayName.trim(),
            agreedToTerms: agreed,
          })
        }}
        className="space-y-4"
      >
        {field('email', 'メールアドレス', 'email', 'email')}
        {field('password', 'パスワード', 'password', 'new-password', '8 文字以上')}
        {field('handle', 'ユーザーID', 'text', 'username', '英数字と _、3〜15 文字。あとから変更できません', '@')}
        {field('displayName', '表示名', 'text', 'nickname', '50 文字以内')}
        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-1" />
          <span>
            <a href="#" className="text-sky-600 hover:underline">利用規約</a>と
            <a href="#" className="text-sky-600 hover:underline">プライバシーポリシー</a>に同意します
          </span>
        </label>
        {errorOf('agreedToTerms') && <p className="text-xs text-red-600">{errorOf('agreedToTerms')}</p>}
        {formError && (
          <p role="alert" className="text-sm text-red-600">
            {formError}
          </p>
        )}
        <button type="submit" disabled={mutation.isPending} className={submitClass}>
          {mutation.isPending ? '登録中…' : '登録する'}
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
