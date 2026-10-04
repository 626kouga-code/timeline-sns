import { createContext, useContext } from 'react'

export const ToastContext = createContext<(message: string) => void>(() => {})

/** 画面下部に短いメッセージを数秒表示する関数を返す */
export const useToast = () => useContext(ToastContext)
