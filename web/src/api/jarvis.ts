import { api, ApiError, query } from './client'
import type { ActivatorState, AlertList, Health, Me, Module, Schedule, SessionInfo } from './types'

/**
 * The module commands answer {} when the module confirmed, and the module's last known state when it didn't answer
 * in time (historical contract of the API).
 */
function confirmed(answer: object | undefined): void {
  if (answer && Object.keys(answer).length > 0 && 'id' in answer)
    throw new ApiError(504, "Le module n'a pas répondu")
}

export const auth = {
  me: () => api<Me>('/auth/me', { handleUnauthorized: false }),
  login: (username: string, password: string, remember: boolean) =>
    api<{ username: string }>('/auth/login', {
      method: 'POST',
      body: { username, password, remember },
      handleUnauthorized: false,
    }),
  logout: () => api<object>('/auth/logout', { method: 'POST', handleUnauthorized: false }),
  changePassword: (current: string, next: string) =>
    api<object>('/auth/password', { method: 'POST', body: { current, next } }),
  sessions: () => api<{ sessions: SessionInfo[] }>('/auth/sessions').then((r) => r.sessions),
  revokeSession: (id: string) => api<object>('/auth/sessions/revoke' + query({ id }), { method: 'POST' }),
}

export const modules = {
  list: () => api<{ modules: Module[] }>('/module/list').then((r) => r.modules),
  activate: async (id: string, stateValue: ActivatorState) =>
    confirmed(await api<object>('/module/activate' + query({ id, stateValue }))),
  enable: async (id: string, enable: boolean) => confirmed(await api<object>('/module/enable' + query({ id, enable }))),
  updateBattery: async (id: string) => {
    const answer = await api<{ batteryLevel?: number }>('/module/update/battery' + query({ id }))
    confirmed(answer)
    return answer.batteryLevel
  },
  edit: (id: string, name: string, room: string) =>
    api<Module>('/module/edit' + query({ id }), { method: 'POST', body: { name, room } }),
  discover: () => api<object>('/module/discover', { method: 'POST' }),
}

export const schedules = {
  list: () => api<Schedule[]>('/schedules/list'),
  /** Replaces every schedule (nothing changes if one is invalid). */
  save: (all: Schedule[]) => api<object>('/schedules/edit', { method: 'POST', body: all }),
}

export const alerts = {
  list: () => api<AlertList>('/alerts/list'),
  read: (id?: number) => api<object>('/alerts/read' + (id === undefined ? '' : query({ id })), { method: 'POST' }),
  clear: () => api<object>('/alerts/clear', { method: 'POST' }),
}

export const hub = {
  health: () => api<Health>('/health'),
}
