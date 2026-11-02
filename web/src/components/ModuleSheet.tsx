import { BatteryFull, BatteryLow, BatteryMedium, BatteryWarning, LoaderCircle, Plug, RefreshCw, Workflow } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import type { Module } from '../api/types'
import {
  useActivate,
  useEditModule,
  useEnableModule,
  useModules,
  useSchedules,
  useUpdateBattery,
} from '../hooks/queries'
import { dateTime, timeAgo } from '../lib/format'
import { hasBattery, isOn, lightPercent, moduleName, moduleStatus, rooms, typeInfo } from '../lib/modules'
import { modulesOf, scheduleTitle } from '../lib/schedules'
import { SignalBars } from './SignalBars'
import { Group, Row, RowIcon, TextInput } from './ui/Group'
import { Sheet } from './ui/Sheet'
import { Toggle } from './ui/Toggle'

export function ModuleSheet({ moduleId, onClose }: { moduleId: string | null; onClose: () => void }) {
  const { data: modules = [] } = useModules()
  const module = modules.find((m) => m.id === moduleId)
  return (
    <Sheet open={!!module} onClose={onClose} title={module ? moduleName(module) : ''}>
      {module && <ModuleDetails key={module.id} module={module} allModules={modules} onClose={onClose} />}
    </Sheet>
  )
}

function ModuleDetails({ module, allModules, onClose }: { module: Module; allModules: Module[]; onClose: () => void }) {
  const info = typeInfo(module)
  const enable = useEnableModule()
  const battery = useUpdateBattery()
  const { data: schedules = [] } = useSchedules()
  const navigate = useNavigate()
  const modulesById = useMemo(() => new Map(allModules.map((m) => [m.id, m])), [allModules])
  const linked = schedules.filter((s) => modulesOf(s).has(module.id))

  return (
    <>
      <Hero module={module} />

      <LabelsForm module={module} knownRooms={rooms(allModules)} />

      <Group title="État">
        <Row label="Activé">
          <Toggle
            label="Activé"
            checked={module.enabled}
            disabled={!module.connected || enable.isPending}
            onChange={(value) => enable.mutate({ module, enable: value })}
          />
        </Row>
        <Row
          label="Connexion"
          value={
            <span className={module.connected ? 'text-ios-green' : 'text-ios-orange'}>
              {module.connected ? 'Connecté' : 'Pas de réponse'}
            </span>
          }
        />
        <Row label="Dernier contact" value={module.lastSeen ? <span title={dateTime(module.lastSeen)}>{timeAgo(module.lastSeen)}</span> : '—'} />
      </Group>

      {hasBattery(module) && (
        <Group
          title="Batterie"
          footer={module.sleeper ? 'Le module dort entre deux appuis : il transmet son niveau à chaque réveil.' : undefined}
        >
          <Row label="Niveau" icon={<BatteryIcon module={module} />} value={`${Math.round(module.batteryLevel)} %`} />
          {module.batteryState && (
            <Row
              label="État"
              value={
                <span className={module.batteryState === 'OK' ? '' : 'text-ios-orange'}>
                  {{ OK: 'Bon', REPLACE_SOON: 'À remplacer bientôt', REPLACE: 'À remplacer' }[module.batteryState]}
                </span>
              }
            />
          )}
          {module.batteryDaysLeft !== undefined && (
            <Row label="Autonomie estimée" value={`≈ ${module.batteryDaysLeft} jours`} />
          )}
          {!module.sleeper && (
            <Row
              label="Mesurer maintenant"
              onClick={() => battery.mutate(module)}
              icon={
                <RowIcon className="bg-ios-green">
                  {battery.isPending ? <LoaderCircle className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
                </RowIcon>
              }
            />
          )}
        </Group>
      )}

      <Group title="Radio">
        <Row label="Signal" value={<span className="inline-flex items-center gap-2"><SignalBars score={module.receptionSignal} /> {module.receptionSignal}/10</span>} />
        <Row label="Nœud radio" value={`#${module.id}`} />
        <Row
          label="Alimentation"
          value={
            <span className="inline-flex items-center gap-1.5">
              {module.sleeper ? 'Pile, en veille' : (<><Plug className="size-4" /> Secteur</>)}
            </span>
          }
        />
        <Row label="Type" value={info.label} />
      </Group>

      <Group
        title="Automatisations"
        footer={linked.length === 0 ? "Ce module n'est utilisé par aucune automatisation." : undefined}
      >
        {linked.map((schedule) => (
          <Row
            key={schedule.id ?? scheduleTitle(schedule, modulesById)}
            label={scheduleTitle(schedule, modulesById)}
            icon={<RowIcon className="bg-ios-orange"><Workflow className="size-4" /></RowIcon>}
            chevron
            onClick={() => {
              onClose()
              navigate('/automatisations', { state: { edit: schedule.id } })
            }}
          />
        ))}
        <Row
          label="Nouvelle automatisation"
          onClick={() => {
            onClose()
            navigate('/automatisations', { state: { create: module.id } })
          }}
          value={<span className="text-ios-blue">+</span>}
        />
      </Group>
    </>
  )
}

function BatteryIcon({ module }: { module: Module }) {
  if (module.batteryState === 'REPLACE') return <BatteryWarning className="size-5 text-ios-red" />
  if (module.batteryLevel < 20 || module.batteryState === 'REPLACE_SOON') return <BatteryLow className="size-5 text-ios-orange" />
  if (module.batteryLevel < 60) return <BatteryMedium className="size-5 text-ios-green" />
  return <BatteryFull className="size-5 text-ios-green" />
}

/** Big control at the top of the sheet. */
function Hero({ module }: { module: Module }) {
  const activate = useActivate()
  const info = typeInfo(module)
  const Icon = info.icon
  const usable = module.connected && module.enabled
  const pending = activate.isPending

  if (module.type === 'STATIC_RELAY_ACTIVATOR') {
    const on = isOn(module)
    return (
      <div className="mb-6 flex flex-col items-center gap-3 pt-2">
        <button
          type="button"
          disabled={!usable || pending}
          onClick={() => activate.mutate({ module, state: on ? 'OFF' : 'ON' })}
          aria-label={on ? 'Éteindre' : 'Allumer'}
          className="relative h-52 w-28 overflow-hidden rounded-[34px] bg-neutral-300 shadow-inner transition active:scale-[0.98] disabled:opacity-60 dark:bg-neutral-700"
        >
          <span
            className={`absolute inset-x-0 bottom-0 bg-white transition-all duration-300 dark:bg-neutral-100 ${on ? 'h-full' : 'h-0'}`}
          />
          <Icon className={`relative mx-auto size-8 ${on ? info.accent : 'text-neutral-500'}`} strokeWidth={2.4} />
        </button>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{pending ? 'Envoi…' : moduleStatus(module)}</p>
      </div>
    )
  }

  if (module.type === 'PULSE_RELAY_ACTIVATOR') {
    return (
      <div className="mb-6 flex flex-col items-center gap-3 pt-2">
        <button
          type="button"
          disabled={!usable || pending}
          onClick={() => activate.mutate({ module, state: 'ON' })}
          className="grid size-32 place-items-center rounded-full bg-white shadow-lg transition active:scale-95 disabled:opacity-60 dark:bg-cell-dark"
        >
          {pending ? (
            <LoaderCircle className="size-10 animate-spin text-ios-yellow" />
          ) : (
            <Icon className="size-12 text-ios-yellow" fill="currentColor" fillOpacity={0.25} />
          )}
        </button>
        <p className="max-w-xs text-center text-sm text-neutral-500 dark:text-neutral-400">
          {!module.connected
            ? 'Pas de réponse'
            : 'Chaque appui bascule le télérupteur. Son état réel (allumé ou éteint) ne peut pas être lu.'}
        </p>
      </div>
    )
  }

  const value =
    module.type === 'LIGHT_SENSOR'
      ? `${lightPercent(module)} %`
      : module.type === 'SWITCH_SENSOR'
        ? module.sensorValue
          ? 'Activé'
          : 'Désactivé'
        : moduleStatus(module)
  return (
    <div className="mb-6 flex flex-col items-center gap-2 pt-2">
      <span className="grid size-24 place-items-center rounded-full bg-white shadow-lg dark:bg-cell-dark">
        <Icon className={`size-11 ${info.accent}`} />
      </span>
      <p className="text-2xl font-semibold">{module.connected ? value : 'Pas de réponse'}</p>
      {module.type === 'LIGHT_SENSOR' && module.connected && (
        <div className="h-1.5 w-40 overflow-hidden rounded-full bg-neutral-300 dark:bg-neutral-700">
          <div className="h-full rounded-full bg-ios-yellow" style={{ width: `${lightPercent(module)}%` }} />
        </div>
      )}
    </div>
  )
}

function LabelsForm({ module, knownRooms }: { module: Module; knownRooms: string[] }) {
  const edit = useEditModule()
  const [name, setName] = useState(module.name ?? '')
  const [room, setRoom] = useState(module.room ?? '')
  const dirty = name.trim() !== (module.name ?? '') || room.trim() !== (module.room ?? '')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (dirty) edit.mutate({ id: module.id, name: name.trim(), room: room.trim() })
  }

  return (
    <form onSubmit={submit}>
      <Group title="Module">
        <Row label="Nom">
          <TextInput
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={`${typeInfo(module).short} ${module.id}`}
            maxLength={40}
            className="text-right"
            aria-label="Nom"
          />
        </Row>
        <Row label="Pièce">
          <TextInput
            value={room}
            onChange={(e) => setRoom(e.target.value)}
            placeholder="Aucune"
            maxLength={40}
            list="jarvis-rooms"
            className="text-right"
            aria-label="Pièce"
          />
          <datalist id="jarvis-rooms">
            {knownRooms.map((r) => (
              <option key={r} value={r} />
            ))}
          </datalist>
        </Row>
        {dirty && (
          <button type="submit" disabled={edit.isPending} className="flex min-h-11 w-full items-center justify-center px-4 text-[17px] font-semibold text-ios-blue">
            {edit.isPending ? <LoaderCircle className="size-5 animate-spin" /> : 'Enregistrer'}
          </button>
        )}
      </Group>
    </form>
  )
}
