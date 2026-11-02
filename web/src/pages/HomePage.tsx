import { BatteryLow, LoaderCircle, Plus, RadioTower, Search, TriangleAlert, WifiOff } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { CircleButton, PageHeader } from '../components/Layout'
import { ModuleSheet } from '../components/ModuleSheet'
import { ModuleTile } from '../components/ModuleTile'
import { Group, Row, RowIcon } from '../components/ui/Group'
import { Sheet } from '../components/ui/Sheet'
import { useAlerts, useDiscover, useHealth, useModules } from '../hooks/queries'
import { errorMessage } from '../lib/format'
import { groupByRoom, isBatteryLow } from '../lib/modules'

export function HomePage() {
  const { data: modules, isPending, error } = useModules()
  const { data: alerts } = useAlerts()
  const { data: health } = useHealth()
  const [openId, setOpenId] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)

  const offline = modules?.filter((m) => !m.connected).length ?? 0
  const lowBattery = modules?.filter((m) => m.connected && isBatteryLow(m)).length ?? 0
  const connected = (modules?.length ?? 0) - offline

  return (
    <>
      <PageHeader
        title="Maison"
        subtitle={
          modules &&
          (modules.length === 0
            ? 'Aucun module pour le moment'
            : `${connected} module${connected > 1 ? 's' : ''} connecté${connected > 1 ? 's' : ''} sur ${modules.length}`)
        }
        actions={
          <CircleButton label="Ajouter un module" onClick={() => setAdding(true)}>
            <Plus className="size-5" strokeWidth={2.5} />
          </CircleButton>
        }
      />

      {/* Status summary, like the Home app's category buttons */}
      <div className="-mx-4 mb-8 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
        {health && !health.radio && (
          <StatusChip to="/alertes" icon={<RadioTower className="size-4 text-ios-red" />} label="Radio" value="Arrêtée" />
        )}
        {!!alerts?.active && (
          <StatusChip
            to="/alertes"
            icon={<TriangleAlert className="size-4 text-ios-orange" />}
            label="Alertes"
            value={`${alerts.active} active${alerts.active > 1 ? 's' : ''}`}
          />
        )}
        {offline > 0 && (
          <StatusChip icon={<WifiOff className="size-4 text-ios-orange" />} label="Hors ligne" value={String(offline)} />
        )}
        {lowBattery > 0 && (
          <StatusChip icon={<BatteryLow className="size-4 text-ios-orange" />} label="Piles faibles" value={String(lowBattery)} />
        )}
        {health?.radio && !alerts?.active && offline === 0 && (
          <StatusChip icon={<RadioTower className="size-4 text-ios-green" />} label="Radio" value={`Canal ${health.radioChannel}`} />
        )}
      </div>

      {isPending && (
        <div className="grid place-items-center py-24">
          <LoaderCircle className="size-8 animate-spin text-white/70" />
        </div>
      )}
      {error && !modules && <p className="glass rounded-2xl p-4 text-center">{errorMessage(error)}</p>}

      {modules?.length === 0 && (
        <div className="glass mx-auto max-w-md rounded-[28px] p-8 text-center">
          <Search className="mx-auto mb-3 size-10 text-white/80" />
          <h2 className="mb-1 text-xl font-semibold">Aucun module</h2>
          <p className="mb-5 text-[15px] text-white/75">
            Les modules apparaissent ici dès qu'ils se présentent au hub, au démarrage ou après une recherche.
          </p>
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="rounded-full bg-white px-5 py-2 font-semibold text-neutral-900 transition active:scale-95"
          >
            Ajouter un module
          </button>
        </div>
      )}

      {modules &&
        groupByRoom(modules).map(([room, list]) => (
          <section key={room} className="mb-8">
            <h2 className="mb-3 text-[20px] font-bold tracking-tight">{room}</h2>
            <div className="grid grid-cols-2 gap-2.5 sm:gap-3 lg:grid-cols-3 xl:grid-cols-4">
              {list.map((module) => (
                <ModuleTile key={module.id} module={module} onOpen={() => setOpenId(module.id)} />
              ))}
            </div>
          </section>
        ))}

      <ModuleSheet moduleId={openId} onClose={() => setOpenId(null)} />
      <AddModuleSheet open={adding} onClose={() => setAdding(false)} />
    </>
  )
}

function StatusChip({ icon, label, value, to }: { icon: ReactNode; label: string; value: string; to?: string }) {
  const content = (
    <>
      <span className="grid size-7 place-items-center rounded-full bg-white/90">{icon}</span>
      <span className="text-left leading-tight">
        <span className="block text-[13px] font-semibold">{label}</span>
        <span className="block text-[12px] text-white/70">{value}</span>
      </span>
    </>
  )
  const className = 'glass flex shrink-0 items-center gap-2 rounded-full py-1.5 pr-4 pl-1.5 transition'
  return to ? (
    <Link to={to} className={`${className} hover:bg-white/25`}>
      {content}
    </Link>
  ) : (
    <div className={className}>{content}</div>
  )
}

function AddModuleSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const discover = useDiscover()
  return (
    <Sheet open={open} onClose={onClose} title="Ajouter un module">
      <div className="mb-6 flex flex-col items-center px-2 pt-2 text-center">
        <span className="mb-3 grid size-20 place-items-center rounded-full bg-white shadow-lg dark:bg-cell-dark">
          <RadioTower className="size-10 text-ios-blue" />
        </span>
        <p className="text-[15px] text-neutral-600 dark:text-neutral-300">
          Un module flashé avec le firmware JARVIS se présente tout seul au hub quand il démarre. Il apparaît alors dans la
          pièce « Autres », où tu peux le renommer.
        </p>
      </div>
      <Group
        footer="Les modules sur pile dorment entre deux appuis : appuie une fois sur le bouton pour le réveiller."
      >
        <Row
          label="Rechercher les modules"
          icon={
            <RowIcon>
              {discover.isPending ? <LoaderCircle className="size-4 animate-spin" /> : <Search className="size-4" />}
            </RowIcon>
          }
          onClick={() => discover.mutate()}
        />
      </Group>
      <Group title="Avant de commencer">
        <Row label="Même canal radio que le hub" />
        <Row label="Même identifiant de réseau" />
        <Row label="Numéro de nœud unique (2 à 254)" />
      </Group>
    </Sheet>
  )
}
