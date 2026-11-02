import { Bell, House, Settings, Workflow, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useAlerts } from '../hooks/queries'

const TABS: { to: string; label: string; icon: LucideIcon }[] = [
  { to: '/', label: 'Maison', icon: House },
  { to: '/automatisations', label: 'Automatisation', icon: Workflow },
  { to: '/alertes', label: 'Alertes', icon: Bell },
  { to: '/reglages', label: 'Réglages', icon: Settings },
]

export function Layout() {
  const { data } = useAlerts()
  const active = data?.active ?? 0

  return (
    <div className="flex min-h-dvh">
      {/* Large screens : sidebar, like Home on the Mac / iPad */}
      <aside className="glass sticky top-0 hidden h-dvh w-64 shrink-0 flex-col gap-1 border-r border-white/10 p-4 md:flex">
        <div className="mb-6 flex items-center gap-2.5 px-2 pt-2">
          <img src="/favicon.svg" alt="" className="size-8" />
          <span className="text-xl font-bold tracking-tight">JARVIS</span>
        </div>
        {TABS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-3 py-2 text-[15px] font-medium transition ${
                isActive ? 'bg-white/25 text-white' : 'text-white/80 hover:bg-white/10'
              }`
            }
          >
            <Icon className="size-5" />
            <span className="flex-1">{label}</span>
            {to === '/alertes' && active > 0 && <Badge count={active} />}
          </NavLink>
        ))}
      </aside>

      <main className="min-w-0 flex-1 px-4 pt-safe pb-28 sm:px-6 md:px-10 md:pb-10">
        <div className="mx-auto max-w-5xl">
          <Outlet />
        </div>
      </main>

      {/* Phones : tab bar */}
      <nav className="glass fixed inset-x-0 bottom-0 z-40 border-t border-white/10 pb-safe md:hidden">
        <div className="mx-auto flex max-w-md justify-around">
          {TABS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `relative flex flex-1 flex-col items-center gap-0.5 pt-2 pb-1.5 text-[10px] font-medium transition ${
                  isActive ? 'text-white' : 'text-white/55'
                }`
              }
            >
              <Icon className="size-6" />
              {label}
              {to === '/alertes' && active > 0 && (
                <span className="absolute top-1 left-1/2 ml-1.5">
                  <Badge count={active} />
                </span>
              )}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}

function Badge({ count }: { count: number }) {
  return (
    <span className="grid h-[18px] min-w-[18px] place-items-center rounded-full bg-ios-red px-1 text-[11px] leading-none font-semibold text-white">
      {count > 99 ? '99+' : count}
    </span>
  )
}

/** Large title of a page, with optional buttons on the right. */
export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="mb-6 flex items-end justify-between gap-4 pt-4 md:pt-8">
      <div className="min-w-0">
        <h1 className="text-[34px] leading-tight font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-0.5 text-[15px] text-white/75">{subtitle}</p>}
      </div>
      {actions && <div className="flex shrink-0 gap-2 pb-1">{actions}</div>}
    </header>
  )
}

export function CircleButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="glass grid size-9 place-items-center rounded-full text-white transition hover:bg-white/25 active:scale-95"
    >
      {children}
    </button>
  )
}
