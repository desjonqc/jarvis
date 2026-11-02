import { CircleDot, Clock, Power, Sun, ToggleRight, Workflow, type LucideIcon } from 'lucide-react'
import type { Module, Schedule, ScheduleElement, ScheduleEvent } from '../api/types'
import { hour } from './format'
import { moduleName } from './modules'

export type ModulesById = Map<string, Module>

/** crypto.randomUUID only exists on HTTPS : the web app is served over plain HTTP on the local network. */
export function newId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
}

/** The schedules created before the web app have no id. */
export function withIds(schedules: Schedule[]): Schedule[] {
  return schedules.map((s, index) => (s.id ? s : { ...s, id: `legacy-${index}` }))
}

export function rawToPercent(raw: number) {
  return Math.round((raw / 1023) * 100)
}

export function percentToRaw(percent: number) {
  return Math.round((Math.min(100, Math.max(0, percent)) / 100) * 1023)
}

function quoted(modules: ModulesById, id: string) {
  return `« ${moduleName(modules.get(id), id)} »`
}

export function describeEvent(event: ScheduleEvent, modules: ModulesById): string {
  switch (event.type) {
    case 'HOUR':
      return `Chaque jour à ${hour(event.args.value)}`
    case 'ENABLE':
      return `${quoted(modules, event.args.id)} est ${event.args.value ? 'activé' : 'désactivé'}`
    case 'SENSOR': {
      const module = modules.get(event.args.id)
      const name = quoted(modules, event.args.id)
      if (module?.type === 'SWITCH_SENSOR' || typeof event.args.value === 'boolean')
        return `${name} passe à ${event.args.value === true || event.args.value === 'true' ? 'activé' : 'désactivé'}`
      if (module?.type === 'LIGHT_SENSOR' || typeof event.args.value === 'number') {
        const percent = rawToPercent(Number(event.args.value ?? 0))
        const comparator = event.args.comparator ?? 0
        const word = comparator < 0 ? 'dépasse' : comparator > 0 ? 'passe sous' : 'atteint'
        return `La luminosité de ${name} ${word} ${percent} %`
      }
      return `${name} est pressé`
    }
    case 'COMBINATION':
      return event.args.events
        .map((e) => describeEvent(e, modules))
        .join(event.args.combination === 'and' ? ' et ' : ' ou ')
  }
}

export function describeElement(element: ScheduleElement, modules: ModulesById): string {
  switch (element.type) {
    case 'WAIT':
      return `Attendre ${duration(element.args.value)}`
    case 'ENABLE':
      return `${element.args.value ? 'Activer' : 'Désactiver'} ${quoted(modules, element.args.id)}`
    case 'ACTIVATOR': {
      const module = modules.get(element.args.id)
      const name = quoted(modules, element.args.id)
      if (module?.type === 'PULSE_RELAY_ACTIVATOR') return `Basculer ${name}`
      if (element.args.value === 'ON') return `Allumer ${name}`
      if (element.args.value === 'OFF') return `Éteindre ${name}`
      return `Inverser ${name}`
    }
  }
}

export function duration(ms: number): string {
  const seconds = Math.round(ms / 1000)
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest ? `${minutes} min ${rest} s` : `${minutes} min`
}

export function scheduleTitle(schedule: Schedule, modules: ModulesById): string {
  return schedule.name?.trim() || describeEvent(schedule.event, modules)
}

export function scheduleSummary(schedule: Schedule, modules: ModulesById): string {
  const actions = schedule.elements.filter((e) => e.type !== 'WAIT').map((e) => describeElement(e, modules))
  return actions.length ? actions.join(', ') : 'Aucune action'
}

export function eventIcon(event: ScheduleEvent, modules: ModulesById): LucideIcon {
  switch (event.type) {
    case 'HOUR':
      return Clock
    case 'ENABLE':
      return Power
    case 'COMBINATION':
      return Workflow
    case 'SENSOR': {
      const type = modules.get(event.args.id)?.type
      if (type === 'LIGHT_SENSOR') return Sun
      if (type === 'SWITCH_SENSOR') return ToggleRight
      return CircleDot
    }
  }
}

/** Ids of the modules used by a schedule. */
export function modulesOf(schedule: Schedule): Set<string> {
  const ids = new Set<string>()
  const visit = (event: ScheduleEvent) => {
    if (event.type === 'COMBINATION') event.args.events.forEach(visit)
    else if (event.type !== 'HOUR') ids.add(event.args.id)
  }
  visit(schedule.event)
  for (const element of schedule.elements) if (element.type !== 'WAIT') ids.add(element.args.id)
  return ids
}
