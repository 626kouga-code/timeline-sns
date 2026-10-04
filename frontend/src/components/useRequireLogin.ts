import { useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/context'

// 未ログインでいいね・コメントなどを押したらログイン画面へ誘導し、ログイン後は元の画面へ戻す
export function useRequireLogin() {
  const { me } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  return (action: () => void) => {
    if (me) action()
    else navigate('/login', { state: { from: location.pathname } })
  }
}
