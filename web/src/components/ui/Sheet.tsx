import { X } from 'lucide-react'
import { useEffect, useId, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface SheetProps {
  open: boolean
  onClose: () => void
  title?: ReactNode
  /** iOS like header buttons ("Annuler" / "OK") ; a close button is shown when both are missing */
  leading?: ReactNode
  trailing?: ReactNode
  children: ReactNode
}

/** A card sliding from the bottom on phones, centered on large screens (like the accessory details in Home). */
export function Sheet({ open, onClose, title, leading, trailing, children }: SheetProps) {
  const titleId = useId()

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
    }
  }, [open, onClose])

  if (!open) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center md:items-center md:p-6">
      <div className="absolute inset-0 animate-fade-in bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        className="relative flex max-h-[94dvh] w-full animate-sheet-up flex-col overflow-hidden rounded-t-[28px] bg-grouped text-neutral-900 shadow-2xl md:max-w-lg md:animate-fade-in md:rounded-[28px] dark:bg-grouped-dark dark:text-white"
      >
        <div className="mx-auto mt-2 h-1.5 w-10 shrink-0 rounded-full bg-neutral-300 md:hidden dark:bg-neutral-600" />
        <header className="grid shrink-0 grid-cols-[1fr_auto_1fr] items-center gap-2 px-4 pt-2 pb-3 md:pt-4">
          <div className="justify-self-start">{leading}</div>
          <h2 id={titleId} className="truncate text-center text-[17px] font-semibold">
            {title}
          </h2>
          <div className="justify-self-end">
            {trailing ??
              (!leading && (
                <button
                  type="button"
                  onClick={onClose}
                  aria-label="Fermer"
                  className="grid size-8 place-items-center rounded-full bg-neutral-200 text-neutral-500 transition hover:bg-neutral-300 dark:bg-neutral-700 dark:text-neutral-300"
                >
                  <X className="size-4" strokeWidth={3} />
                </button>
              ))}
          </div>
        </header>
        <div className="overflow-y-auto overscroll-contain px-4 pb-[max(env(safe-area-inset-bottom),1.5rem)]">
          {children}
        </div>
      </div>
    </div>,
    document.body,
  )
}

export function SheetButton({
  children,
  onClick,
  bold,
  disabled,
  type = 'button',
  form,
}: {
  children: ReactNode
  onClick?: () => void
  bold?: boolean
  disabled?: boolean
  type?: 'button' | 'submit'
  form?: string
}) {
  return (
    <button
      type={type}
      form={form}
      onClick={onClick}
      disabled={disabled}
      className={`text-[17px] text-ios-blue transition disabled:text-neutral-400 ${bold ? 'font-semibold' : ''}`}
    >
      {children}
    </button>
  )
}
