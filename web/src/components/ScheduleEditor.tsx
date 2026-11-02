import { ArrowDown, ArrowUp, Clock, Minus, Plus, Power, Timer, Zap } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import type {
  ActivatorState,
  Module,
  Schedule,
  ScheduleElement,
  ScheduleEvent,
  SimpleEvent,
} from '../api/types'
import { hour } from '../lib/format'
import { isActivator, moduleName } from '../lib/modules'
import { describeEvent, newId, percentToRaw, rawToPercent } from '../lib/schedules'
import { Group, Row, RowIcon, TextInput } from './ui/Group'
import { Segmented } from './ui/Segmented'
import { Sheet, SheetButton } from './ui/Sheet'
import { Toggle } from './ui/Toggle'

interface Draft {
  id: string
  name: string
  enabled: boolean
  combination: 'and' | 'or'
  conditions: SimpleEvent[]
  /** a trigger the editor can't show (nested combinations) : kept as it is */
  advanced: ScheduleEvent | null
  elements: ScheduleElement[]
  /** unknown fields of the schedule, written back untouched */
  extra: Record<string, unknown>
}

function toDraft(schedule: Schedule | null, firstModule?: Module): Draft {
  if (!schedule) {
    const condition: SimpleEvent = firstModule ? defaultSensorEvent(firstModule) : { type: 'HOUR', args: { value: 8 } }
    return { id: newId(), name: '', enabled: true, combination: 'and', conditions: [condition], advanced: null, elements: [], extra: {} }
  }
  const { id, name, enabled, event, elements, ...extra } = schedule
  const base = { id: id ?? newId(), name: name ?? '', enabled: enabled !== false, elements: [...elements], extra }
  if (event.type !== 'COMBINATION') return { ...base, combination: 'and', conditions: [event], advanced: null }
  const simple = event.args.events.every((e) => e.type !== 'COMBINATION')
  return simple
    ? { ...base, combination: event.args.combination, conditions: event.args.events as SimpleEvent[], advanced: null }
    : { ...base, combination: 'and', conditions: [], advanced: event }
}

function fromDraft(draft: Draft): Schedule {
  const event: ScheduleEvent =
    draft.advanced ??
    (draft.conditions.length === 1
      ? draft.conditions[0]
      : { type: 'COMBINATION', args: { combination: draft.combination, events: draft.conditions } })
  return {
    ...draft.extra,
    id: draft.id,
    ...(draft.name.trim() ? { name: draft.name.trim() } : {}),
    enabled: draft.enabled,
    event,
    elements: draft.elements,
  }
}

function defaultSensorEvent(module: Module): SimpleEvent {
  if (module.type === 'SWITCH_SENSOR') return { type: 'SENSOR', args: { id: module.id, value: true } }
  if (module.type === 'LIGHT_SENSOR') return { type: 'SENSOR', args: { id: module.id, comparator: 1, value: percentToRaw(30) } }
  return { type: 'SENSOR', args: { id: module.id } }
}

function defaultAction(module: Module | undefined): ScheduleElement {
  return module
    ? { type: 'ACTIVATOR', args: { id: module.id, value: 'ON' } }
    : { type: 'WAIT', args: { value: 5000 } }
}

/** A module id may be unknown for now (module not seen since the hub restarted) : only an empty one is refused. */
function isValid(draft: Draft) {
  const known = (id: string) => id !== ''
  const conditionsOk =
    draft.advanced !== null ||
    (draft.conditions.length > 0 && draft.conditions.every((c) => c.type === 'HOUR' || known(c.args.id)))
  const actionsOk =
    draft.elements.some((e) => e.type !== 'WAIT') &&
    draft.elements.every((e) => (e.type === 'WAIT' ? e.args.value > 0 : known(e.args.id)))
  return conditionsOk && actionsOk
}

interface EditorProps {
  open: boolean
  schedule: Schedule | null
  /** module to start a new automation with */
  initialModuleId?: string
  modules: Module[]
  onSave: (schedule: Schedule) => void
  onDelete?: () => void
  onClose: () => void
}

export function ScheduleEditor(props: EditorProps) {
  return (
    <Sheet
      open={props.open}
      onClose={props.onClose}
      title={props.schedule ? 'Automatisation' : 'Nouvelle automatisation'}
      leading={<SheetButton onClick={props.onClose}>Annuler</SheetButton>}
      trailing={<SheetButton bold type="submit" form="schedule-editor">OK</SheetButton>}
    >
      {props.open && <EditorBody key={props.schedule?.id ?? 'new'} {...props} />}
    </Sheet>
  )
}

function EditorBody({ schedule, initialModuleId, modules, onSave, onDelete, onClose }: EditorProps) {
  const byId = useMemo(() => new Map(modules.map((m) => [m.id, m])), [modules])
  const sensors = modules.filter((m) => !isActivator(m))
  const activators = modules.filter(isActivator)
  const [draft, setDraft] = useState<Draft>(() => {
    const initial = initialModuleId ? byId.get(initialModuleId) : undefined
    const created = toDraft(schedule, initial && !isActivator(initial) ? initial : sensors[0])
    if (!schedule && initial && isActivator(initial)) created.elements = [defaultAction(initial)]
    else if (!schedule) created.elements = activators[0] ? [defaultAction(activators[0])] : []
    return created
  })
  const valid = isValid(draft)
  const update = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }))

  const setCondition = (index: number, event: SimpleEvent) =>
    update({ conditions: draft.conditions.map((c, i) => (i === index ? event : c)) })
  const setElement = (index: number, element: ScheduleElement) =>
    update({ elements: draft.elements.map((e, i) => (i === index ? element : e)) })
  const moveElement = (index: number, delta: number) => {
    const elements = [...draft.elements]
    const [moved] = elements.splice(index, 1)
    elements.splice(index + delta, 0, moved)
    update({ elements })
  }

  return (
    <form
      id="schedule-editor"
      onSubmit={(event) => {
        event.preventDefault()
        if (valid) onSave(fromDraft(draft))
      }}
    >
      <Group>
        <Row label="Nom">
          <TextInput
            value={draft.name}
            onChange={(e) => update({ name: e.target.value })}
            placeholder="Facultatif"
            maxLength={60}
            className="text-right"
            aria-label="Nom"
          />
        </Row>
        <Row label="Activée">
          <Toggle label="Activée" checked={draft.enabled} onChange={(enabled) => update({ enabled })} />
        </Row>
      </Group>

      {/* ---------------------------------------------------------- trigger */}
      <h3 className="mb-2 px-1 text-[20px] font-bold">Quand…</h3>
      {draft.advanced ? (
        <Group footer="Ce déclencheur combine plusieurs groupes de conditions : il n'est pas modifiable ici.">
          <Row label={describeEvent(draft.advanced, byId)} />
        </Group>
      ) : (
        <>
          {draft.conditions.length > 1 && (
            <div className="mb-3">
              <Segmented
                label="Combinaison"
                value={draft.combination}
                onChange={(combination) => update({ combination })}
                options={[
                  { value: 'and', label: 'Toutes les conditions' },
                  { value: 'or', label: "L'une des conditions" },
                ]}
              />
            </div>
          )}
          {draft.conditions.map((condition, index) => (
            <ConditionCard
              key={index}
              condition={condition}
              modules={modules}
              sensors={sensors}
              onChange={(event) => setCondition(index, event)}
              onRemove={
                draft.conditions.length > 1
                  ? () => update({ conditions: draft.conditions.filter((_, i) => i !== index) })
                  : undefined
              }
            />
          ))}
          <AddButton
            onClick={() =>
              update({ conditions: [...draft.conditions, sensors[0] ? defaultSensorEvent(sensors[0]) : { type: 'HOUR', args: { value: 8 } }] })
            }
          >
            Ajouter une condition
          </AddButton>
          {draft.conditions.length > 1 && draft.combination === 'and' && (
            <p className="-mt-3 mb-6 px-4 text-[13px] text-neutral-500 dark:text-neutral-400">
              Les événements doivent arriver à moins de 20 secondes d'intervalle. Une heure sert de condition : « pendant
              l'heure de 22:00 ».
            </p>
          )}
        </>
      )}

      {/* ---------------------------------------------------------- actions */}
      <h3 className="mb-2 px-1 text-[20px] font-bold">Alors…</h3>
      {draft.elements.length === 0 && (
        <p className="mb-3 px-4 text-[15px] text-neutral-500 dark:text-neutral-400">Ajoute au moins une action.</p>
      )}
      {draft.elements.map((element, index) => (
        <ActionCard
          key={index}
          element={element}
          modules={modules}
          activators={activators}
          onChange={(e) => setElement(index, e)}
          onRemove={() => update({ elements: draft.elements.filter((_, i) => i !== index) })}
          onUp={index > 0 ? () => moveElement(index, -1) : undefined}
          onDown={index < draft.elements.length - 1 ? () => moveElement(index, 1) : undefined}
        />
      ))}
      <AddButton onClick={() => update({ elements: [...draft.elements, defaultAction(activators[0])] })}>
        Ajouter une action
      </AddButton>

      {!valid && (
        <p className="mb-6 px-4 text-[13px] text-ios-orange">
          Il faut au moins une condition et une action (hors attente), avec un module choisi pour chacune.
        </p>
      )}

      {onDelete && (
        <Group>
          <Row
            label="Supprimer l'automatisation"
            destructive
            onClick={() => {
              if (window.confirm('Supprimer cette automatisation ?')) {
                onDelete()
                onClose()
              }
            }}
          />
        </Group>
      )}
    </form>
  )
}

// ---------------------------------------------------------------- conditions

function ConditionCard({
  condition,
  modules,
  sensors,
  onChange,
  onRemove,
}: {
  condition: SimpleEvent
  modules: Module[]
  sensors: Module[]
  onChange: (event: SimpleEvent) => void
  onRemove?: () => void
}) {
  const byId = new Map(modules.map((m) => [m.id, m]))

  const setKind = (kind: SimpleEvent['type']) => {
    if (kind === condition.type) return
    if (kind === 'HOUR') onChange({ type: 'HOUR', args: { value: 8 } })
    else if (kind === 'ENABLE') onChange({ type: 'ENABLE', args: { id: modules[0]?.id ?? '', value: true } })
    else onChange(sensors[0] ? defaultSensorEvent(sensors[0]) : { type: 'SENSOR', args: { id: '' } })
  }

  return (
    <Card onRemove={onRemove} removeLabel="Retirer la condition">
      <div className="px-4 pt-3 pb-2">
        <Segmented
          label="Type de condition"
          value={condition.type}
          onChange={setKind}
          options={[
            { value: 'SENSOR', label: 'Capteur' },
            { value: 'HOUR', label: 'Heure' },
            { value: 'ENABLE', label: 'Module activé' },
          ]}
        />
      </div>

      {condition.type === 'HOUR' && (
        <Row label="Chaque jour à" icon={<RowIcon className="bg-ios-blue"><Clock className="size-4" /></RowIcon>}>
          <Select
            label="Heure"
            value={String(condition.args.value)}
            onChange={(v) => onChange({ type: 'HOUR', args: { value: Number(v) } })}
            options={Array.from({ length: 24 }, (_, h) => ({ value: String(h), label: hour(h) }))}
          />
        </Row>
      )}

      {condition.type === 'ENABLE' && (
        <>
          <ModuleRow
            label="Module"
            value={condition.args.id}
            modules={modules}
            onChange={(id) => onChange({ type: 'ENABLE', args: { ...condition.args, id } })}
          />
          <div className="px-4 py-2">
            <Segmented
              label="État"
              value={condition.args.value ? 'on' : 'off'}
              onChange={(v) => onChange({ type: 'ENABLE', args: { ...condition.args, value: v === 'on' } })}
              options={[
                { value: 'on', label: 'est activé' },
                { value: 'off', label: 'est désactivé' },
              ]}
            />
          </div>
        </>
      )}

      {condition.type === 'SENSOR' &&
        (sensors.length === 0 ? (
          <Row label="Aucun capteur connu" value="—" />
        ) : (
          <SensorFields condition={condition} module={byId.get(condition.args.id)} sensors={sensors} onChange={onChange} />
        ))}
    </Card>
  )
}

function SensorFields({
  condition,
  module,
  sensors,
  onChange,
}: {
  condition: Extract<SimpleEvent, { type: 'SENSOR' }>
  module: Module | undefined
  sensors: Module[]
  onChange: (event: SimpleEvent) => void
}) {
  return (
    <>
      <ModuleRow
        label="Capteur"
        value={condition.args.id}
        modules={sensors}
        onChange={(id) => {
          const next = sensors.find((m) => m.id === id)
          if (next) onChange(defaultSensorEvent(next))
        }}
      />
      {module?.type === 'PULSE_SWITCH_SENSOR' && <Row label="Événement" value="est pressé" />}
      {module?.type === 'SWITCH_SENSOR' && (
        <div className="px-4 py-2">
          <Segmented
            label="Valeur"
            value={condition.args.value === false ? 'off' : 'on'}
            onChange={(v) => onChange({ type: 'SENSOR', args: { id: condition.args.id, value: v === 'on' } })}
            options={[
              { value: 'on', label: 'passe à activé' },
              { value: 'off', label: 'passe à désactivé' },
            ]}
          />
        </div>
      )}
      {module?.type === 'LIGHT_SENSOR' && (
        <>
          <div className="px-4 py-2">
            <Segmented
              label="Comparaison"
              value={(condition.args.comparator ?? 0) < 0 ? 'above' : 'below'}
              onChange={(v) =>
                onChange({ type: 'SENSOR', args: { ...condition.args, comparator: v === 'above' ? -1 : 1 } })
              }
              options={[
                { value: 'below', label: 'passe sous' },
                { value: 'above', label: 'dépasse' },
              ]}
            />
          </div>
          <Row label="Luminosité">
            <input
              type="number"
              min={0}
              max={100}
              aria-label="Luminosité en pourcent"
              value={rawToPercent(Number(condition.args.value ?? 0))}
              onChange={(e) =>
                onChange({ type: 'SENSOR', args: { ...condition.args, value: percentToRaw(Number(e.target.value)) } })
              }
              className="w-16 bg-transparent text-right text-neutral-500 outline-none dark:text-neutral-400"
            />
            <span className="text-neutral-500">%</span>
          </Row>
        </>
      )}
    </>
  )
}

// ---------------------------------------------------------------- actions

function ActionCard({
  element,
  modules,
  activators,
  onChange,
  onRemove,
  onUp,
  onDown,
}: {
  element: ScheduleElement
  modules: Module[]
  activators: Module[]
  onChange: (element: ScheduleElement) => void
  onRemove: () => void
  onUp?: () => void
  onDown?: () => void
}) {
  const setKind = (kind: ScheduleElement['type']) => {
    if (kind === element.type) return
    if (kind === 'WAIT') onChange({ type: 'WAIT', args: { value: 5000 } })
    else if (kind === 'ENABLE') onChange({ type: 'ENABLE', args: { id: modules[0]?.id ?? '', value: false } })
    else onChange(defaultAction(activators[0]))
  }
  const target = element.type === 'WAIT' ? undefined : modules.find((m) => m.id === element.args.id)

  return (
    <Card
      onRemove={onRemove}
      removeLabel="Retirer l'action"
      extra={
        <>
          <IconButton label="Monter" disabled={!onUp} onClick={onUp}>
            <ArrowUp className="size-4" />
          </IconButton>
          <IconButton label="Descendre" disabled={!onDown} onClick={onDown}>
            <ArrowDown className="size-4" />
          </IconButton>
        </>
      }
    >
      <div className="px-4 pt-3 pb-2">
        <Segmented
          label="Type d'action"
          value={element.type}
          onChange={setKind}
          options={[
            { value: 'ACTIVATOR', label: 'Commander' },
            { value: 'ENABLE', label: 'Activer / désactiver' },
            { value: 'WAIT', label: 'Attendre' },
          ]}
        />
      </div>

      {element.type === 'WAIT' && (
        <Row label="Durée" icon={<RowIcon className="bg-ios-gray"><Timer className="size-4" /></RowIcon>}>
          <input
            type="number"
            min={1}
            step={1}
            aria-label="Durée en secondes"
            value={Math.round(element.args.value / 1000)}
            onChange={(e) =>
              onChange({ type: 'WAIT', args: { value: Math.max(1, Math.round(Number(e.target.value) || 0)) * 1000 } })
            }
            className="w-20 bg-transparent text-right text-neutral-500 outline-none dark:text-neutral-400"
          />
          <span className="text-neutral-500">s</span>
        </Row>
      )}

      {element.type === 'ACTIVATOR' &&
        (activators.length === 0 ? (
          <Row label="Aucun actionneur connu" value="—" />
        ) : (
          <>
            <ModuleRow
              label="Module"
              icon={<RowIcon className="bg-ios-yellow"><Zap className="size-4" /></RowIcon>}
              value={element.args.id}
              modules={activators}
              onChange={(id) => onChange({ type: 'ACTIVATOR', args: { id, value: 'ON' } })}
            />
            {target?.type === 'PULSE_RELAY_ACTIVATOR' ? (
              <Row label="Action" value="Basculer" />
            ) : (
              <div className="px-4 py-2">
                <Segmented<ActivatorState>
                  label="Action"
                  value={element.args.value}
                  onChange={(value) => onChange({ type: 'ACTIVATOR', args: { ...element.args, value } })}
                  options={[
                    { value: 'ON', label: 'Allumer' },
                    { value: 'OFF', label: 'Éteindre' },
                    ...(element.args.value === 'ON_BACK' ? [{ value: 'ON_BACK' as const, label: 'Inverser' }] : []),
                  ]}
                />
              </div>
            )}
          </>
        ))}

      {element.type === 'ENABLE' && (
        <>
          <ModuleRow
            label="Module"
            icon={<RowIcon className="bg-ios-green"><Power className="size-4" /></RowIcon>}
            value={element.args.id}
            modules={modules}
            onChange={(id) => onChange({ type: 'ENABLE', args: { ...element.args, id } })}
          />
          <div className="px-4 py-2">
            <Segmented
              label="État"
              value={element.args.value ? 'on' : 'off'}
              onChange={(v) => onChange({ type: 'ENABLE', args: { ...element.args, value: v === 'on' } })}
              options={[
                { value: 'on', label: 'Activer' },
                { value: 'off', label: 'Désactiver' },
              ]}
            />
          </div>
        </>
      )}
    </Card>
  )
}

// ---------------------------------------------------------------- small pieces

function Card({
  children,
  onRemove,
  removeLabel,
  extra,
}: {
  children: ReactNode
  onRemove?: () => void
  removeLabel: string
  extra?: ReactNode
}) {
  return (
    <div className="relative mb-3 divide-y divide-neutral-200 rounded-xl bg-white dark:divide-neutral-700/70 dark:bg-cell-dark">
      {children}
      {(onRemove || extra) && (
        <div className="flex items-center justify-end gap-1 px-2 py-1.5">
          {extra}
          {onRemove && (
            <IconButton label={removeLabel} onClick={onRemove} danger>
              <Minus className="size-4" strokeWidth={3} />
            </IconButton>
          )}
        </div>
      )}
    </div>
  )
}

function IconButton({
  label,
  onClick,
  disabled,
  danger,
  children,
}: {
  label: string
  onClick?: () => void
  disabled?: boolean
  danger?: boolean
  children: ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className={`grid size-7 place-items-center rounded-full transition disabled:opacity-30 ${
        danger ? 'bg-ios-red text-white' : 'bg-neutral-200 text-neutral-600 dark:bg-neutral-700 dark:text-neutral-300'
      }`}
    >
      {children}
    </button>
  )
}

function AddButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mb-6 flex min-h-11 w-full items-center gap-3 rounded-xl bg-white px-4 text-[17px] text-ios-blue transition active:bg-neutral-100 dark:bg-cell-dark"
    >
      <span className="grid size-6 place-items-center rounded-full bg-ios-green text-white">
        <Plus className="size-4" strokeWidth={3} />
      </span>
      {children}
    </button>
  )
}

function Select({
  value,
  onChange,
  options,
  label,
}: {
  value: string
  onChange: (value: string) => void
  options: { value: string; label: string }[]
  label: string
}) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="max-w-[60%] cursor-pointer appearance-none truncate bg-transparent text-right text-neutral-500 outline-none dark:text-neutral-400"
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  )
}

function ModuleRow({
  label,
  value,
  modules,
  onChange,
  icon,
}: {
  label: string
  value: string
  modules: Module[]
  onChange: (id: string) => void
  icon?: ReactNode
}) {
  const options = modules.map((m) => ({ value: m.id, label: moduleName(m) }))
  if (value && !modules.some((m) => m.id === value)) options.unshift({ value, label: `Module ${value} (inconnu)` })
  if (!value) options.unshift({ value: '', label: 'Choisir…' })
  return (
    <Row label={label} icon={icon}>
      <Select label={label} value={value} onChange={onChange} options={options} />
    </Row>
  )
}
