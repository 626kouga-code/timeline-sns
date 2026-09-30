import { useLocation, useNavigate } from 'react-router'
import { useStore } from '../mock/store'

// 未ログインでいいね・コメントなどを押したらログイン画面へ誘導する
export function useRequireLogin() {
  const { me } = useStore()
  const navigate = useNavigate()
  const location = useLocation()
  return (action: () => void) => {
    if (me) action()
    else navigate('/login', { state: { from: location.pathname } })
  }
}
