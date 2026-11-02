import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import * as jarvis from '../api/jarvis'
import type { ActivatorState, Module, Schedule } from '../api/types'
import { useToast } from '../components/ui/Toast'
import { errorMessage } from '../lib/format'

export const keys = {
  modules: ['modules'] as const,
  schedules: ['schedules'] as const,
  alerts: ['alerts'] as const,
  sessions: ['sessions'] as const,
  health: ['health'] as const,
}

export function useModules() {
  return useQuery({ queryKey: keys.modules, queryFn: jarvis.modules.list, refetchInterval: 5_000 })
}

export function useSchedules() {
  return useQuery({ queryKey: keys.schedules, queryFn: jarvis.schedules.list })
}

export function useAlerts() {
  return useQuery({ queryKey: keys.alerts, queryFn: jarvis.alerts.list, refetchInterval: 15_000 })
}

export function useSessions() {
  return useQuery({ queryKey: keys.sessions, queryFn: jarvis.auth.sessions })
}

export function useHealth() {
  return useQuery({ queryKey: keys.health, queryFn: jarvis.hub.health, refetchInterval: 30_000 })
}

/** Changes a module in the cached list right away (the next poll brings the real state). */
function usePatchModule() {
  const client = useQueryClient()
  return (id: string, patch: Partial<Module>) =>
    client.setQueryData<Module[]>(keys.modules, (list) => list?.map((m) => (m.id === id ? { ...m, ...patch } : m)))
}

export function useActivate() {
  const client = useQueryClient()
  const patch = usePatchModule()
  const toast = useToast()
  return useMutation({
    mutationFn: ({ module, state }: { module: Module; state: ActivatorState }) =>
      jarvis.modules.activate(module.id, state),
    onMutate: ({ module, state }) => {
      const previous = module.stateValue
      if (module.type === 'STATIC_RELAY_ACTIVATOR') patch(module.id, { stateValue: state })
      return { previous }
    },
    onError: (error, { module }, context) => {
      if (module.type === 'STATIC_RELAY_ACTIVATOR') patch(module.id, { stateValue: context?.previous })
      toast.error(errorMessage(error))
    },
    onSettled: () => client.invalidateQueries({ queryKey: keys.modules }),
  })
}

export function useEnableModule() {
  const client = useQueryClient()
  const patch = usePatchModule()
  const toast = useToast()
  return useMutation({
    mutationFn: ({ module, enable }: { module: Module; enable: boolean }) => jarvis.modules.enable(module.id, enable),
    onMutate: ({ module, enable }) => {
      patch(module.id, { enabled: enable })
      return { previous: module.enabled }
    },
    onError: (error, { module }, context) => {
      patch(module.id, { enabled: context?.previous ?? module.enabled })
      toast.error(errorMessage(error))
    },
    onSettled: () => client.invalidateQueries({ queryKey: keys.modules }),
  })
}

export function useUpdateBattery() {
  const client = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: (module: Module) => jarvis.modules.updateBattery(module.id),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => client.invalidateQueries({ queryKey: keys.modules }),
  })
}

export function useEditModule() {
  const client = useQueryClient()
  const patch = usePatchModule()
  const toast = useToast()
  return useMutation({
    mutationFn: ({ id, name, room }: { id: string; name: string; room: string }) => jarvis.modules.edit(id, name, room),
    onSuccess: (module) => {
      patch(module.id, module)
      toast.success('Module enregistré')
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => client.invalidateQueries({ queryKey: keys.modules }),
  })
}

export function useDiscover() {
  const client = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: jarvis.modules.discover,
    onSuccess: () => {
      toast.success('Recherche lancée : les nouveaux modules vont apparaître')
      // the modules answer within a few seconds
      setTimeout(() => client.invalidateQueries({ queryKey: keys.modules }), 3_000)
    },
    onError: (error) => toast.error(errorMessage(error)),
  })
}

export function useSaveSchedules() {
  const client = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: (all: Schedule[]) => jarvis.schedules.save(all),
    onMutate: async (all) => {
      await client.cancelQueries({ queryKey: keys.schedules })
      const previous = client.getQueryData<Schedule[]>(keys.schedules)
      client.setQueryData(keys.schedules, all)
      return { previous }
    },
    onError: (error, _all, context) => {
      client.setQueryData(keys.schedules, context?.previous)
      toast.error(
        error instanceof ApiError && error.status === 400
          ? 'Automatisation refusée par le hub (paramètres invalides)'
          : errorMessage(error),
      )
    },
    onSettled: () => client.invalidateQueries({ queryKey: keys.schedules }),
  })
}

export function useReadAlerts() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id?: number) => jarvis.alerts.read(id),
    onSettled: () => client.invalidateQueries({ queryKey: keys.alerts }),
  })
}

export function useClearAlerts() {
  const client = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: jarvis.alerts.clear,
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => client.invalidateQueries({ queryKey: keys.alerts }),
  })
}

export function useRevokeSession() {
  const client = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: jarvis.auth.revokeSession,
    onSuccess: () => toast.success('Appareil déconnecté'),
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => client.invalidateQueries({ queryKey: keys.sessions }),
  })
}

export function useChangePassword() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ current, next }: { current: string; next: string }) => jarvis.auth.changePassword(current, next),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.sessions }),
  })
}
