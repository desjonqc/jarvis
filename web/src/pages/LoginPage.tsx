import { ArrowRight, LoaderCircle } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'
import { errorMessage } from '../lib/format'

export function LoginPage() {
  const { login, expired } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(true)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!username || !password || pending) return
    setPending(true)
    setError(null)
    try {
      await login(username.trim(), password, remember)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 0 ? 'Le hub JARVIS ne répond pas' : errorMessage(e))
      setPassword('')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center p-6">
      <form onSubmit={submit} className="glass w-full max-w-sm rounded-[32px] p-8 text-center">
        <img src="/favicon.svg" alt="" className="mx-auto mb-4 size-16 drop-shadow-lg" />
        <h1 className="text-[28px] font-bold tracking-tight">JARVIS</h1>
        <p className="mb-7 text-[15px] text-white/75">Connecte-toi pour piloter la maison</p>

        {expired && !error && (
          <p className="mb-4 rounded-xl bg-white/15 px-3 py-2 text-sm text-white/90">Ta session a expiré.</p>
        )}

        <div className="overflow-hidden rounded-2xl bg-white/90 text-left text-neutral-900 shadow-inner">
          <input
            autoFocus
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="Identifiant"
            aria-label="Identifiant"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="w-full border-b border-neutral-200 bg-transparent px-4 py-3.5 text-[17px] outline-none placeholder:text-neutral-400"
          />
          <div className="flex items-center">
            <input
              type="password"
              autoComplete="current-password"
              placeholder="Mot de passe"
              aria-label="Mot de passe"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="min-w-0 flex-1 bg-transparent px-4 py-3.5 text-[17px] outline-none placeholder:text-neutral-400"
            />
            <button
              type="submit"
              disabled={!username || !password || pending}
              aria-label="Se connecter"
              className="mr-2 grid size-9 shrink-0 place-items-center rounded-full bg-ios-blue text-white transition disabled:bg-neutral-300"
            >
              {pending ? <LoaderCircle className="size-5 animate-spin" /> : <ArrowRight className="size-5" strokeWidth={2.5} />}
            </button>
          </div>
        </div>

        <label className="mt-5 flex cursor-pointer items-center justify-center gap-2 text-[15px] text-white/85 select-none">
          <input
            type="checkbox"
            checked={remember}
            onChange={(e) => setRemember(e.target.checked)}
            className="size-4 accent-ios-blue"
          />
          Rester connecté sur cet appareil
        </label>

        <p role="alert" className="mt-4 min-h-5 text-sm font-medium text-[#ffd0cc]">
          {error}
        </p>
      </form>
    </div>
  )
}
