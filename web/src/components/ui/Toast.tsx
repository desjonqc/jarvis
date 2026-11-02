import { CircleAlert, CircleCheck } from 'lucide-react'
import { createContext, use, useCallback, useMemo, useRef, useState, type ReactNode } from 'react'

interface Toast {
  id: number
  kind: 'success' | 'error'
  message: string
}

interface ToastApi {
  success: (message: string) => void
  error: (message: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const push = useCallback((kind: Toast['kind'], message: string) => {
    const id = nextId.current++
    setToasts((list) => [...list.filter((t) => t.message !== message), { id, kind, message }].slice(-3))
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 4_000)
  }, [])

  const api = useMemo<ToastApi>(
    () => ({ success: (m) => push('success', m), error: (m) => push('error', m) }),
    [push],
  )

  return (
    <ToastContext value={api}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-0 z-[100] flex flex-col items-center gap-2 pt-safe">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className="flex max-w-[90vw] animate-toast-in items-center gap-2 rounded-full bg-neutral-900/80 px-4 py-2.5 text-sm font-medium text-white shadow-xl backdrop-blur-xl"
          >
            {toast.kind === 'success' ? (
              <CircleCheck className="size-5 shrink-0 text-ios-green" />
            ) : (
              <CircleAlert className="size-5 shrink-0 text-ios-red" />
            )}
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext>
  )
}

// eslint-disable-next-line react/only-export-components
export function useToast() {
  const context = use(ToastContext)
  if (!context) throw new Error('useToast outside of ToastProvider')
  return context
}
