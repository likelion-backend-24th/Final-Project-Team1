import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

interface ToastItem { id: number; msg: string; type: 'success' | 'error' | 'info' }

interface ToastContextType {
  toast: (msg: string, type?: ToastItem['type']) => void
}

const ToastContext = createContext<ToastContextType | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const toast = useCallback((msg: string, type: ToastItem['type'] = 'info') => {
    const id = Date.now()
    setItems(prev => [...prev, { id, msg, type }])
    setTimeout(() => setItems(prev => prev.filter(i => i.id !== id)), 3000)
  }, [])

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="toast-container">
        {items.map(i => (
          <div key={i.id} className={`toast ${i.type}`}>{i.msg}</div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be inside ToastProvider')
  return ctx.toast
}
