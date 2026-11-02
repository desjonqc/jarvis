export type ModuleType =
  | 'STATIC_RELAY_ACTIVATOR'
  | 'PULSE_RELAY_ACTIVATOR'
  | 'SWITCH_SENSOR'
  | 'PULSE_SWITCH_SENSOR'
  | 'LIGHT_SENSOR'

export type ModuleState =
  | 'DISABLED'
  | 'DISCONNECTED'
  | 'BATTERY_LOW'
  | 'SIGNAL_LOW'
  | 'WORKING'
  | 'CONNECTING'
  | 'SLEEPING'

export type ActivatorState = 'ON' | 'OFF' | 'ON_BACK'

export type BatteryState = 'OK' | 'REPLACE_SOON' | 'REPLACE'

/** A module as returned by /module/list and /module/info. */
export interface Module {
  id: string
  type: ModuleType
  state: ModuleState
  name?: string
  room?: string
  batteryLevel: number
  receptionSignal: number
  sleeper?: boolean
  connected: boolean
  enabled: boolean
  lastSeen: number
  batteryState?: BatteryState
  batteryDaysLeft?: number
  /** activators */
  stateValue?: ActivatorState
  /** switch (boolean) and light (0..1023) sensors */
  sensorValue?: boolean | number
}

// ---------------------------------------------------------------- schedules ("liens")

export type SensorEvent = {
  type: 'SENSOR'
  /** comparator 0 = equals ; -1 = sensor value above `value` ; 1 = sensor value below `value` */
  args: { id: string; comparator?: number; value?: boolean | number | string }
}
export type HourEvent = { type: 'HOUR'; args: { value: number } }
export type EnableEvent = { type: 'ENABLE'; args: { id: string; value: boolean } }
export type CombinationEvent = {
  type: 'COMBINATION'
  args: { combination: 'and' | 'or'; events: ScheduleEvent[] }
}
export type ScheduleEvent = SensorEvent | HourEvent | EnableEvent | CombinationEvent
export type SimpleEvent = SensorEvent | HourEvent | EnableEvent

export type ActivatorElement = { type: 'ACTIVATOR'; args: { id: string; value: ActivatorState } }
/** value in milliseconds */
export type WaitElement = { type: 'WAIT'; args: { value: number } }
export type EnableElement = { type: 'ENABLE'; args: { id: string; value: boolean } }
export type ScheduleElement = ActivatorElement | WaitElement | EnableElement

/**
 * The hub stores each schedule as given : `id`, `name` and `enabled` are only used by the web app
 * (and `enabled: false` pauses the schedule).
 */
export interface Schedule {
  id?: string
  name?: string
  enabled?: boolean
  event: ScheduleEvent
  elements: ScheduleElement[]
}

// ---------------------------------------------------------------- alerts

export type AlertType = 'MODULE_DISCONNECTED' | 'BATTERY_LOW' | 'BATTERY_CRITICAL' | 'SIGNAL_LOW' | 'RADIO_DOWN'

export interface Alert {
  id: number
  type: AlertType
  severity: 'WARNING' | 'CRITICAL'
  moduleId: string | null
  createdAt: number
  resolvedAt: number | null
  read: boolean
}

export interface AlertList {
  alerts: Alert[]
  active: number
  unread: number
}

// ---------------------------------------------------------------- auth / hub

export interface Me {
  username: string | null
  method: 'session' | 'apikey'
  sessionId?: string
  expiresAt?: number
}

export interface SessionInfo {
  id: string
  userAgent: string
  remember: boolean
  createdAt: number
  lastUsedAt: number
  expiresAt: number
  current: boolean
}

export interface Health {
  status: string
  radio: boolean
  radioDriver: string
  radioChannel: number
  modules: number
}
