import { ChevronRight } from 'lucide-react'
import type { InputHTMLAttributes, ReactNode } from 'react'

/** Inset grouped list, like the iOS settings. */
export function Group({ title, footer, children }: { title?: ReactNode; footer?: ReactNode; children: ReactNode }) {
  return (
    <section className="mb-6">
      {title && <h3 className="mb-1.5 px-4 text-[13px] text-neutral-500 uppercase dark:text-neutral-400">{title}</h3>}
      <div className="divide-y divide-neutral-200 overflow-hidden rounded-xl bg-white dark:divide-neutral-700/70 dark:bg-cell-dark">
        {children}
      </div>
      {footer && <p className="mt-1.5 px-4 text-[13px] text-neutral-500 dark:text-neutral-400">{footer}</p>}
    </section>
  )
}

interface RowProps {
  label?: ReactNode
  value?: ReactNode
  icon?: ReactNode
  onClick?: () => void
  chevron?: boolean
  children?: ReactNode
  destructive?: boolean
}

export function Row({ label, value, icon, onClick, chevron, children, destructive }: RowProps) {
  const content = (
    <>
      {icon && <span className="shrink-0">{icon}</span>}
      {label !== undefined && (
        <span className={`min-w-0 flex-1 truncate text-left ${destructive ? 'text-ios-red' : ''}`}>{label}</span>
      )}
      {value !== undefined && (
        <span className="max-w-[60%] truncate text-right text-neutral-500 dark:text-neutral-400">{value}</span>
      )}
      {children}
      {chevron && <ChevronRight className="size-4 shrink-0 text-neutral-400" strokeWidth={2.5} />}
    </>
  )
  const className = 'flex min-h-11 w-full items-center gap-3 px-4 py-2 text-[17px]'
  return onClick ? (
    <button type="button" onClick={onClick} className={`${className} transition active:bg-neutral-100 dark:active:bg-neutral-700`}>
      {content}
    </button>
  ) : (
    <div className={className}>{content}</div>
  )
}

/** An icon in a colored rounded square, like in the iOS settings. */
export function RowIcon({ children, className = 'bg-ios-blue' }: { children: ReactNode; className?: string }) {
  return <span className={`grid size-7 place-items-center rounded-[7px] text-white ${className}`}>{children}</span>
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`min-w-0 flex-1 bg-transparent text-[17px] outline-none placeholder:text-neutral-400 ${props.className ?? ''}`}
    />
  )
}
