import { createContext, useContext } from 'react'

export interface ToastItem { id: number; msg: string; type: 'success' | 'error' | 'info' }

export interface ToastContextType {
  toast: (msg: string, type?: ToastItem['type']) => void
}

export const ToastContext = createContext<ToastContextType | null>(null)

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be inside ToastProvider')
  return ctx.toast
}
