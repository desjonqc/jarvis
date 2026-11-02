import { KeyRound, LoaderCircle, LogOut, Monitor, RadioTower, Search, Smartphone, UserRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { PageHeader } from '../components/Layout'
import { Group, Row, RowIcon, TextInput } from '../components/ui/Group'
import { Sheet, SheetButton } from '../components/ui/Sheet'
import { useChangePassword, useDiscover, useHealth, useModules, useRevokeSession, useSessions } from '../hooks/queries'
import { useToast } from '../components/ui/Toast'
import { dateTime, describeDevice, errorMessage, timeAgo } from '../lib/format'

/** The settings use the light grouped style of iOS, on a card over the wallpaper. */
export function SettingsPage() {
  const { username, logout } = useAuth()
  const { data: sessions, isPending: sessionsPending } = useSessions()
  const { data: health } = useHealth()
  const { data: modules } = useModules()
  const revoke = useRevokeSession()
  const discover = useDiscover()
  const [passwordOpen, setPasswordOpen] = useState(false)

  return (
    <>
      <PageHeader title="Réglages" />

      <div className="mx-auto max-w-2xl rounded-[28px] bg-grouped p-4 text-neutral-900 shadow-xl sm:p-6 dark:bg-grouped-dark dark:text-white">
        <Group title="Compte">
          <Row
            label={username ?? '—'}
            icon={<RowIcon className="bg-ios-gray"><UserRound className="size-4" /></RowIcon>}
          />
          <Row
            label="Changer le mot de passe"
            icon={<RowIcon className="bg-ios-blue"><KeyRound className="size-4" /></RowIcon>}
            chevron
            onClick={() => setPasswordOpen(true)}
          />
        </Group>

        <Group
          title="Appareils connectés"
          footer="Déconnecte un appareil que tu ne reconnais pas, puis change ton mot de passe."
        >
          {sessionsPending && <Row label={<LoaderCircle className="size-5 animate-spin text-neutral-400" />} />}
          {sessions?.map((session) => {
            const device = describeDevice(session.userAgent)
            const DeviceIcon = /iPhone|Android/.test(device) ? Smartphone : Monitor
            return (
              <div key={session.id} className="flex min-h-14 items-center gap-3 px-4 py-2">
                <RowIcon className={session.current ? 'bg-ios-green' : 'bg-ios-gray'}>
                  <DeviceIcon className="size-4" />
                </RowIcon>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[17px]">
                    {device}
                    {session.current && <span className="ml-2 text-[13px] text-ios-green">Cet appareil</span>}
                  </div>
                  <div className="truncate text-[13px] text-neutral-500 dark:text-neutral-400" title={dateTime(session.createdAt)}>
                    Actif {timeAgo(session.lastUsedAt)} · connecté le {dateTime(session.createdAt)}
                  </div>
                </div>
                {!session.current && (
                  <button
                    type="button"
                    disabled={revoke.isPending}
                    onClick={() => revoke.mutate(session.id)}
                    className="shrink-0 text-[15px] text-ios-red"
                  >
                    Déconnecter
                  </button>
                )}
              </div>
            )
          })}
        </Group>

        <Group title="Hub JARVIS">
          <Row
            label="Radio"
            icon={<RowIcon className={health?.radio ? 'bg-ios-green' : 'bg-ios-red'}><RadioTower className="size-4" /></RowIcon>}
            value={health ? (health.radio ? 'En marche' : 'Arrêtée') : '…'}
          />
          <Row label="Pilote" value={health ? (health.radioDriver === 'simulated' ? 'Simulé' : 'nRF24L01+') : '…'} />
          <Row label="Canal" value={health?.radioChannel ?? '…'} />
          <Row label="Modules" value={modules?.length ?? health?.modules ?? '…'} />
          <Row
            label="Rechercher des modules"
            icon={
              <RowIcon>
                {discover.isPending ? <LoaderCircle className="size-4 animate-spin" /> : <Search className="size-4" />}
              </RowIcon>
            }
            onClick={() => discover.mutate()}
          />
        </Group>

        <Group>
          <Row label="Se déconnecter" destructive icon={<LogOut className="size-5 text-ios-red" />} onClick={() => logout()} />
        </Group>
      </div>

      <PasswordSheet open={passwordOpen} onClose={() => setPasswordOpen(false)} />
    </>
  )
}

function PasswordSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const change = useChangePassword()
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)

  const close = () => {
    setCurrent('')
    setNext('')
    setConfirm('')
    setError(null)
    onClose()
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (next.length < 8) return setError('Le nouveau mot de passe doit contenir au moins 8 caractères.')
    if (next !== confirm) return setError('Les deux mots de passe sont différents.')
    setError(null)
    change.mutate(
      { current, next },
      {
        onSuccess: () => {
          toast.success('Mot de passe changé. Les autres appareils ont été déconnectés.')
          close()
        },
        onError: (e) => setError(e instanceof ApiError && e.status === 403 ? 'Mot de passe actuel incorrect.' : errorMessage(e)),
      },
    )
  }

  return (
    <Sheet
      open={open}
      onClose={close}
      title="Mot de passe"
      leading={<SheetButton onClick={close}>Annuler</SheetButton>}
      trailing={
        <SheetButton bold type="submit" form="password-form" disabled={!current || !next || !confirm || change.isPending}>
          {change.isPending ? <LoaderCircle className="size-5 animate-spin" /> : 'OK'}
        </SheetButton>
      }
    >
      <form id="password-form" onSubmit={submit}>
        <Group>
          <Row label="Actuel">
            <TextInput type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} placeholder="requis" className="text-right" aria-label="Mot de passe actuel" />
          </Row>
        </Group>
        <Group footer="8 caractères minimum. Tes autres appareils seront déconnectés.">
          <Row label="Nouveau">
            <TextInput type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} placeholder="requis" className="text-right" aria-label="Nouveau mot de passe" />
          </Row>
          <Row label="Confirmer">
            <TextInput type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} placeholder="requis" className="text-right" aria-label="Confirmer le mot de passe" />
          </Row>
        </Group>
        {error && <p role="alert" className="mb-4 px-4 text-[15px] text-ios-red">{error}</p>}
      </form>
    </Sheet>
  )
}
