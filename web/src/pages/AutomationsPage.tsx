import { LoaderCircle, Plus, Workflow } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { Schedule } from '../api/types'
import { CircleButton, PageHeader } from '../components/Layout'
import { ScheduleEditor } from '../components/ScheduleEditor'
import { Toggle } from '../components/ui/Toggle'
import { useModules, useSaveSchedules, useSchedules } from '../hooks/queries'
import { errorMessage } from '../lib/format'
import { eventIcon, scheduleSummary, scheduleTitle, withIds } from '../lib/schedules'

/** scheduleId absent = new automation (moduleId : the module to start it with) */
type EditorState = { scheduleId?: string; moduleId?: string } | null

export function AutomationsPage() {
  const { data: rawSchedules, isPending, error } = useSchedules()
  const { data: modules = [] } = useModules()
  const save = useSaveSchedules()
  const location = useLocation()
  const navigate = useNavigate()
  // Opened from a module's details : edit one of its automations, or create one with it
  const [editor, setEditor] = useState<EditorState>(() => {
    const state = location.state as { edit?: string; create?: string } | null
    if (state?.edit) return { scheduleId: state.edit }
    if (state?.create) return { moduleId: state.create }
    return null
  })

  const schedules = useMemo(() => withIds(rawSchedules ?? []), [rawSchedules])
  const byId = useMemo(() => new Map(modules.map((m) => [m.id, m])), [modules])

  const editing = editor?.scheduleId ? schedules.find((s) => s.id === editor.scheduleId) : undefined

  // The navigation state is used once : a reload must not open the editor again
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const persist = (all: Schedule[]) => save.mutate(all)

  const upsert = (schedule: Schedule) => {
    const exists = schedules.some((s) => s.id === schedule.id)
    persist(exists ? schedules.map((s) => (s.id === schedule.id ? schedule : s)) : [...schedules, schedule])
    setEditor(null)
  }

  return (
    <>
      <PageHeader
        title="Automatisation"
        subtitle="Les liens entre tes modules : quand un événement arrive, JARVIS agit."
        actions={
          <CircleButton label="Nouvelle automatisation" onClick={() => setEditor({})}>
            <Plus className="size-5" strokeWidth={2.5} />
          </CircleButton>
        }
      />

      {isPending && (
        <div className="grid place-items-center py-24">
          <LoaderCircle className="size-8 animate-spin text-white/70" />
        </div>
      )}
      {error && <p className="glass rounded-2xl p-4 text-center">{errorMessage(error)}</p>}

      {rawSchedules && schedules.length === 0 && (
        <div className="glass mx-auto max-w-md rounded-[28px] p-8 text-center">
          <Workflow className="mx-auto mb-3 size-10 text-white/80" />
          <h2 className="mb-1 text-xl font-semibold">Aucune automatisation</h2>
          <p className="mb-5 text-[15px] text-white/75">
            Relie un bouton à une lumière, allume le jardin à 20 h, ou désactive un module quand un autre change.
          </p>
          <button
            type="button"
            onClick={() => setEditor({})}
            className="rounded-full bg-white px-5 py-2 font-semibold text-neutral-900 transition active:scale-95"
          >
            Créer une automatisation
          </button>
        </div>
      )}

      <div className="grid gap-2.5 lg:grid-cols-2">
        {schedules.map((schedule) => {
          const Icon = eventIcon(schedule.event, byId)
          const enabled = schedule.enabled !== false
          return (
            <div
              key={schedule.id}
              role="button"
              tabIndex={0}
              onClick={() => setEditor({ scheduleId: schedule.id })}
              onKeyDown={(e) => e.key === 'Enter' && setEditor({ scheduleId: schedule.id })}
              className={`glass flex cursor-pointer items-center gap-3 rounded-[18px] p-3 transition active:scale-[0.99] ${
                enabled ? '' : 'opacity-60'
              }`}
            >
              <span className="grid size-11 shrink-0 place-items-center rounded-full bg-ios-orange/90">
                <Icon className="size-5 text-white" strokeWidth={2.4} />
              </span>
              <div className="min-w-0 flex-1">
                <div className="truncate text-[15px] font-semibold">{scheduleTitle(schedule, byId)}</div>
                <div className="truncate text-[13px] text-white/70">{scheduleSummary(schedule, byId)}</div>
              </div>
              <Toggle
                label={enabled ? "Désactiver l'automatisation" : "Activer l'automatisation"}
                checked={enabled}
                disabled={save.isPending}
                onChange={(value) => persist(schedules.map((s) => (s.id === schedule.id ? { ...s, enabled: value } : s)))}
              />
            </div>
          )
        })}
      </div>

      <ScheduleEditor
        // an automation to edit is shown once the list is loaded
        open={editor !== null && (!editor.scheduleId || !!editing)}
        schedule={editing ?? null}
        initialModuleId={editor?.moduleId}
        modules={modules}
        onSave={upsert}
        onDelete={editing ? () => persist(schedules.filter((s) => s.id !== editing.id)) : undefined}
        onClose={() => setEditor(null)}
      />
    </>
  )
}
