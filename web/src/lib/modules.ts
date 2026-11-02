import { CircleDot, Lightbulb, Power, Sun, ToggleRight, type LucideIcon } from 'lucide-react'
import type { Module, ModuleType } from '../api/types'

interface TypeInfo {
  label: string
  /** default name of a module of this type, with its node id */
  short: string
  icon: LucideIcon
  kind: 'activator' | 'sensor'
  /** tile color when on */
  accent: string
}

export const MODULE_TYPES: Record<ModuleType, TypeInfo> = {
  STATIC_RELAY_ACTIVATOR: { label: 'Relais', short: 'Relais', icon: Power, kind: 'activator', accent: 'text-ios-green' },
  PULSE_RELAY_ACTIVATOR: { label: 'Télérupteur', short: 'Lumière', icon: Lightbulb, kind: 'activator', accent: 'text-ios-yellow' },
  SWITCH_SENSOR: { label: 'Interrupteur', short: 'Interrupteur', icon: ToggleRight, kind: 'sensor', accent: 'text-ios-blue' },
  PULSE_SWITCH_SENSOR: { label: 'Bouton sans fil', short: 'Bouton', icon: CircleDot, kind: 'sensor', accent: 'text-ios-orange' },
  LIGHT_SENSOR: { label: 'Capteur de luminosité', short: 'Luminosité', icon: Sun, kind: 'sensor', accent: 'text-ios-yellow' },
}

export const NO_ROOM = 'Autres'

export function typeInfo(module: Module): TypeInfo {
  return MODULE_TYPES[module.type] ?? { label: module.type, short: 'Module', icon: CircleDot, kind: 'sensor', accent: 'text-ios-gray' }
}

export function moduleName(module: Module | undefined, id?: string | null): string {
  if (!module) return id ? `Module ${id}` : 'Module inconnu'
  return module.name || `${typeInfo(module).short} ${module.id}`
}

export function isActivator(module: Module) {
  return typeInfo(module).kind === 'activator'
}

/** Its state can be read (the pulse relay only toggles a remote switch : its state is unknown). */
export function hasKnownState(module: Module) {
  return module.type === 'STATIC_RELAY_ACTIVATOR' || module.type === 'SWITCH_SENSOR'
}

export function isOn(module: Module): boolean {
  if (module.type === 'STATIC_RELAY_ACTIVATOR') return module.stateValue === 'ON'
  if (module.type === 'SWITCH_SENSOR') return module.sensorValue === true
  return false
}

export function lightPercent(module: Module): number {
  return Math.round((Number(module.sensorValue ?? 0) / 1023) * 100)
}

export function hasBattery(module: Module) {
  return module.batteryState !== undefined || module.sleeper === true
}

export function isBatteryLow(module: Module) {
  return hasBattery(module) && (module.batteryState === 'REPLACE' || module.batteryState === 'REPLACE_SOON' || module.batteryLevel < 20)
}

/** Status line under the name, like "No Response" in the Home app. */
export function moduleStatus(module: Module): string {
  if (!module.connected) return 'Pas de réponse'
  if (!module.enabled) return 'Désactivé'
  switch (module.type) {
    case 'STATIC_RELAY_ACTIVATOR':
      return module.stateValue === 'ON' ? 'Allumé' : 'Éteint'
    case 'PULSE_RELAY_ACTIVATOR':
      return 'Basculer'
    case 'SWITCH_SENSOR':
      return module.sensorValue ? 'Activé' : 'Désactivé'
    case 'LIGHT_SENSOR':
      return `${lightPercent(module)} %`
    case 'PULSE_SWITCH_SENSOR':
      return isBatteryLow(module) ? `Pile faible · ${Math.round(module.batteryLevel)} %` : 'Prêt'
  }
  return ''
}

export function roomOf(module: Module) {
  return module.room?.trim() || NO_ROOM
}

/** Modules grouped by room, rooms sorted (the modules without room last). */
export function groupByRoom(modules: Module[]): [string, Module[]][] {
  const rooms = new Map<string, Module[]>()
  for (const module of [...modules].sort((a, b) => moduleName(a).localeCompare(moduleName(b), 'fr'))) {
    const room = roomOf(module)
    rooms.set(room, [...(rooms.get(room) ?? []), module])
  }
  return [...rooms.entries()].sort(([a], [b]) => {
    if (a === NO_ROOM) return 1
    if (b === NO_ROOM) return -1
    return a.localeCompare(b, 'fr')
  })
}

export function rooms(modules: Module[]): string[] {
  return [...new Set(modules.map((m) => m.room?.trim()).filter((r): r is string => !!r))].sort((a, b) =>
    a.localeCompare(b, 'fr'),
  )
}

/** Signal score 0..10 to 0..4 bars. */
export function signalBars(score: number) {
  if (score >= 8) return 4
  if (score >= 6) return 3
  if (score >= 3) return 2
  if (score >= 1) return 1
  return 0
}
