import { CheckCheck, CircleCheck, LoaderCircle, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { Alert, Module } from '../api/types'
import { CircleButton, PageHeader } from '../components/Layout'
import { ModuleSheet } from '../components/ModuleSheet'
import { useAlerts, useClearAlerts, useModules, useReadAlerts } from '../hooks/queries'
import { alertInfo, alertSubject } from '../lib/alerts'
import { dateTime, errorMessage, timeAgo } from '../lib/format'

export function AlertsPage() {
  const { data, isPending, error } = useAlerts()
  const { data: modules = [] } = useModules()
  const read = useReadAlerts()
  const clear = useClearAlerts()
  const [openId, setOpenId] = useState<string | null>(null)
  const byId = useMemo(() => new Map(modules.map((m) => [m.id, m])), [modules])

  const active = data?.alerts.filter((a) => a.resolvedAt === null) ?? []
  const history = data?.alerts.filter((a) => a.resolvedAt !== null) ?? []
  const unread = data?.unread ?? 0

  // Seen : marked as read after a moment on the page
  const { mutate: markRead } = read
  useEffect(() => {
    if (!unread) return
    const timer = window.setTimeout(() => markRead(undefined), 2_000)
    return () => window.clearTimeout(timer)
  }, [unread, markRead])

  return (
    <>
      <PageHeader
        title="Alertes"
        subtitle={
          data && (active.length ? `${active.length} problème${active.length > 1 ? 's' : ''} en cours` : 'Tout fonctionne')
        }
        actions={
          history.length > 0 && (
            <CircleButton
              label="Effacer l'historique"
              onClick={() => window.confirm("Effacer l'historique des alertes résolues ?") && clear.mutate()}
            >
              <Trash2 className="size-[18px]" />
            </CircleButton>
          )
        }
      />

      {isPending && (
        <div className="grid place-items-center py-24">
          <LoaderCircle className="size-8 animate-spin text-white/70" />
        </div>
      )}
      {error && <p className="glass rounded-2xl p-4 text-center">{errorMessage(error)}</p>}

      {data && active.length === 0 && (
        <div className="glass mb-8 flex items-center gap-4 rounded-[22px] p-5">
          <span className="grid size-12 shrink-0 place-items-center rounded-full bg-white/90">
            <CircleCheck className="size-7 text-ios-green" />
          </span>
          <div>
            <p className="text-[17px] font-semibold">Aucune alerte</p>
            <p className="text-[14px] text-white/75">Tous les modules répondent et leurs piles sont en bon état.</p>
          </div>
        </div>
      )}

      {active.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-3 text-[20px] font-bold tracking-tight">En cours</h2>
          <div className="grid gap-2.5 lg:grid-cols-2">
            {active.map((alert) => (
              <AlertCard key={alert.id} alert={alert} modules={byId} onOpen={setOpenId} />
            ))}
          </div>
        </section>
      )}

      {history.length > 0 && (
        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-[20px] font-bold tracking-tight">Historique</h2>
            {unread > 0 && (
              <button type="button" onClick={() => markRead(undefined)} className="flex items-center gap-1 text-sm text-white/80">
                <CheckCheck className="size-4" /> Tout marquer comme lu
              </button>
            )}
          </div>
          <div className="glass divide-y divide-white/10 overflow-hidden rounded-[22px]">
            {history.map((alert) => {
              const info = alertInfo(alert)
              const Icon = info.icon
              return (
                <div key={alert.id} className="flex items-center gap-3 px-4 py-3">
                  <Icon className="size-5 shrink-0 text-white/60" />
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[15px]">
                      {alertSubject(alert, byId)} · {info.title.toLowerCase()}
                    </div>
                    <div className="text-[13px] text-white/60" title={dateTime(alert.createdAt)}>
                      {timeAgo(alert.createdAt)} — résolu {alert.resolvedAt ? timeAgo(alert.resolvedAt) : ''}
                    </div>
                  </div>
                  {!alert.read && <span className="size-2 shrink-0 rounded-full bg-ios-blue" aria-label="Non lue" />}
                </div>
              )
            })}
          </div>
        </section>
      )}

      <ModuleSheet moduleId={openId} onClose={() => setOpenId(null)} />
    </>
  )
}

function AlertCard({ alert, modules, onOpen }: { alert: Alert; modules: Map<string, Module>; onOpen: (id: string) => void }) {
  const info = alertInfo(alert)
  const Icon = info.icon
  const critical = alert.severity === 'CRITICAL'
  const clickable = alert.moduleId !== null && modules.has(alert.moduleId)
  return (
    <button
      type="button"
      disabled={!clickable}
      onClick={() => alert.moduleId && onOpen(alert.moduleId)}
      className="flex w-full items-start gap-3 rounded-[22px] bg-white/95 p-4 text-left text-neutral-900 shadow-lg shadow-black/10 transition enabled:active:scale-[0.99]"
    >
      <span className={`grid size-10 shrink-0 place-items-center rounded-full ${critical ? 'bg-ios-red' : 'bg-ios-orange'}`}>
        <Icon className="size-5 text-white" strokeWidth={2.4} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-baseline justify-between gap-2">
          <span className="truncate text-[15px] font-semibold">{alertSubject(alert, modules)}</span>
          <span className="shrink-0 text-[12px] text-neutral-500" title={dateTime(alert.createdAt)}>
            {timeAgo(alert.createdAt)}
          </span>
        </span>
        <span className={`block text-[14px] font-medium ${critical ? 'text-ios-red' : 'text-[#c76f00]'}`}>{info.title}</span>
        <span className="mt-0.5 block text-[13px] text-neutral-600">{info.detail}</span>
      </span>
      {!alert.read && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-ios-blue" aria-label="Non lue" />}
    </button>
  )
}
