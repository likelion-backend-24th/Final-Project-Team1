import { useCallback, useState, type ReactNode } from 'react'
import { ToastContext, type ToastItem } from './Toast'

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
