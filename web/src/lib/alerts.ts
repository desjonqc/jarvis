import { BatteryLow, BatteryWarning, RadioTower, SignalLow, WifiOff, type LucideIcon } from 'lucide-react'
import type { Alert, AlertType, Module } from '../api/types'
import { moduleName } from './modules'

const ALERTS: Record<AlertType, { title: string; detail: string; icon: LucideIcon }> = {
  MODULE_DISCONNECTED: {
    title: 'Ne répond plus',
    detail: "Le module n'a pas donné de nouvelles depuis trop longtemps. Vérifie son alimentation et sa portée radio.",
    icon: WifiOff,
  },
  BATTERY_LOW: {
    title: 'Pile faible',
    detail: 'Prévois de remplacer la pile dans les prochaines semaines.',
    icon: BatteryLow,
  },
  BATTERY_CRITICAL: {
    title: 'Pile à remplacer',
    detail: 'La pile ne peut presque plus alimenter la radio : le module risque de ne plus répondre.',
    icon: BatteryWarning,
  },
  SIGNAL_LOW: {
    title: 'Signal faible',
    detail: 'Des commandes peuvent être perdues. Rapproche le module du hub ou ajoute un relais.',
    icon: SignalLow,
  },
  RADIO_DOWN: {
    title: 'Radio du hub arrêtée',
    detail: "Aucun module ne peut être joint. Consulte les logs de JARVIS (câblage SPI, alimentation de l'antenne).",
    icon: RadioTower,
  },
}

export function alertInfo(alert: Alert) {
  return ALERTS[alert.type] ?? { title: alert.type, detail: '', icon: BatteryWarning }
}

export function alertSubject(alert: Alert, modules: Map<string, Module>): string {
  return alert.moduleId === null ? 'Hub JARVIS' : moduleName(modules.get(alert.moduleId), alert.moduleId)
}
