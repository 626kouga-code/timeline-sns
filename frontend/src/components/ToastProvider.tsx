import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { ToastContext } from './toast'

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<string | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const showToast = useCallback((message: string) => {
    setToast(message)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setToast(null), 2500)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <ToastContext.Provider value={showToast}>
      {children}
      {toast && (
        <div
          role="status"
          className="fixed bottom-24 left-1/2 z-[60] -translate-x-1/2 rounded-full bg-sky-500 px-5 py-2.5 text-sm font-semibold text-white shadow-lg sm:bottom-8"
        >
          {toast}
        </div>
      )}
    </ToastContext.Provider>
  )
}
