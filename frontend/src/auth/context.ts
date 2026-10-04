import { createContext, useContext } from 'react'
import type { LoginInput, SignupInput } from '../api/auth'
import type { Me } from '../api/types'

export interface AuthContextValue {
  /** ログイン中のユーザー。未ログインなら null */
  me: Me | null
  login: (input: LoginInput) => Promise<Me>
  signup: (input: SignupInput) => Promise<Me>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth は AuthProvider の内側で使ってください')
  return value
}
