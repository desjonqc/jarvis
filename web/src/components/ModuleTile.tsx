import { BatteryLow, LoaderCircle, TriangleAlert } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { Module } from '../api/types'
import { useActivate } from '../hooks/queries'
import { isActivator, isBatteryLow, isOn, moduleName, moduleStatus, typeInfo } from '../lib/modules'

const LONG_PRESS_MS = 450

/**
 * Accessory tile of the Home app (iOS 16+) : the icon runs the action, the name opens the details. A long press
 * anywhere opens the details too.
 */
export function ModuleTile({ module, onOpen }: { module: Module; onOpen: () => void }) {
  const activate = useActivate()
  const info = typeInfo(module)
  const Icon = info.icon
  const [flash, setFlash] = useState(false)
  const pressTimer = useRef<number | undefined>(undefined)
  const longPressed = useRef(false)

  useEffect(() => () => window.clearTimeout(pressTimer.current), [])

  const pending = activate.isPending && activate.variables?.module.id === module.id
  const usable = module.connected && module.enabled
  const on = isOn(module) || flash
  const lit = on && usable

  const runAction = () => {
    if (!isActivator(module) || !usable || pending) {
      onOpen()
      return
    }
    if (module.type === 'PULSE_RELAY_ACTIVATOR') {
      activate.mutate(
        { module, state: 'ON' },
        {
          onSuccess: () => {
            setFlash(true)
            window.setTimeout(() => setFlash(false), 1200)
          },
        },
      )
    } else {
      activate.mutate({ module, state: module.stateValue === 'ON' ? 'OFF' : 'ON' })
    }
  }

  const guard = (action: () => void) => () => {
    if (longPressed.current) {
      longPressed.current = false
      return
    }
    action()
  }

  const status = flash ? 'Basculé' : pending ? 'Envoi…' : moduleStatus(module)

  return (
    <div
      onPointerDown={() => {
        longPressed.current = false
        pressTimer.current = window.setTimeout(() => {
          longPressed.current = true
          onOpen()
        }, LONG_PRESS_MS)
      }}
      onPointerUp={() => window.clearTimeout(pressTimer.current)}
      onPointerLeave={() => window.clearTimeout(pressTimer.current)}
      onContextMenu={(event) => event.preventDefault()}
      className={`group relative flex min-h-[72px] items-center gap-3 rounded-[18px] py-2 pr-3 pl-2.5 transition duration-200 select-none active:scale-[0.97] ${
        lit ? 'bg-white/95 text-neutral-900 shadow-lg shadow-black/10' : 'glass text-white'
      } ${module.connected ? '' : 'opacity-75'}`}
    >
      <button
        type="button"
        onClick={guard(runAction)}
        aria-label={isActivator(module) ? `Basculer ${moduleName(module)}` : `Ouvrir ${moduleName(module)}`}
        className={`grid size-[46px] shrink-0 place-items-center rounded-full transition ${
          lit ? 'bg-neutral-100' : 'bg-white/15'
        }`}
      >
        {pending ? (
          <LoaderCircle className="size-6 animate-spin" />
        ) : (
          <Icon
            className={`size-6 transition ${lit ? info.accent : 'text-white/80'}`}
            fill={lit && module.type !== 'STATIC_RELAY_ACTIVATOR' ? 'currentColor' : 'none'}
            fillOpacity={0.25}
            strokeWidth={2.2}
          />
        )}
      </button>
      <button type="button" onClick={guard(onOpen)} className="min-w-0 flex-1 text-left">
        <div className="line-clamp-2 text-[15px] leading-tight font-semibold break-words">{moduleName(module)}</div>
        <div
          className={`flex items-center gap-1 truncate text-[13px] leading-tight ${
            !module.connected ? 'text-ios-orange' : lit ? 'text-neutral-500' : 'text-white/65'
          }`}
        >
          {!module.connected && <TriangleAlert className="size-3.5 shrink-0" />}
          {module.connected && isBatteryLow(module) && module.type !== 'PULSE_SWITCH_SENSOR' && (
            <BatteryLow className="size-3.5 shrink-0 text-ios-orange" />
          )}
          <span className="truncate">{status}</span>
        </div>
      </button>
    </div>
  )
}
