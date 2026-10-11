import { useLocation } from 'react-router'

/** いま表示している画面のパスと、ログイン後に戻る先（state.from）を出す（renderWithProviders で画面遷移を確かめる） */
export function LocationProbe() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return (
    <div data-testid="location" data-from={from ?? ''}>
      {location.pathname}
    </div>
  )
}
