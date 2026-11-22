import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { timelineKeys } from '../api/timeline'
import { follow, unfollow, userKeys } from '../api/users'
import { useAuth } from '../auth/context'
import { useToast } from './toast'
import { useRequireLogin } from './useRequireLogin'

interface FollowTarget {
  id: string
  handle: string
  /** 閲覧者がこのユーザーをフォローしている */
  following: boolean
}

/**
 * フォロー・解除ボタン（F-42）。自分自身には出さない。
 * 成功したらプロフィール・一覧・ホームタイムラインを取り直し、取り直し終わるまでは押せないようにする
 * （古い表示のまま押されると、意図と逆の操作になるため）。
 */
export function FollowButton({ user }: { user: FollowTarget }) {
  const { me } = useAuth()
  const queryClient = useQueryClient()
  const showToast = useToast()
  const requireLogin = useRequireLogin()

  const mutation = useMutation({
    mutationFn: (next: boolean) => (next ? follow(user.handle) : unfollow(user.handle)),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: userKeys.all }),
        // フォローした相手の投稿がホームタイムラインに入る（外れる）
        queryClient.invalidateQueries({ queryKey: timelineKeys.all }),
      ]),
    onError: (error) => {
      showToast(error instanceof ApiError ? error.message : 'フォローできませんでした')
    },
  })

  if (me?.id === user.id) return null

  const { following } = user
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation()
        if (mutation.isPending) return
        requireLogin(() => mutation.mutate(!following))
      }}
      disabled={mutation.isPending}
      // 読み上げ名は見えている文字から始める（「フォロー @hanako」「フォロー中 @hanako」）。フォロー中かどうかは aria-pressed でも伝える
      aria-pressed={following}
      className={
        following
          ? 'group shrink-0 rounded-full border border-slate-300 px-4 py-1.5 text-sm font-bold hover:border-red-300 hover:bg-red-50 hover:text-red-600 disabled:opacity-60'
          : 'shrink-0 rounded-full bg-slate-900 px-4 py-1.5 text-sm font-bold text-white hover:bg-slate-700 disabled:opacity-60'
      }
    >
      {following ? (
        <>
          {/* マウスを乗せている間は見た目だけ「解除」に替える。読み上げ名は「フォロー中」のまま */}
          <span className="group-hover:sr-only">フォロー中</span>
          {/* マウスを乗せたときだけの見た目の言い換えなので、読み上げ名には入れない */}
          <span className="hidden group-hover:inline" aria-hidden="true">
            解除
          </span>
        </>
      ) : (
        'フォロー'
      )}{' '}
      <span className="sr-only">@{user.handle}</span>
    </button>
  )
}
