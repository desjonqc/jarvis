import { useQueryClient } from '@tanstack/react-query'
import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '../api/client'
import { auth } from '../api/jarvis'

type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  status: AuthStatus
  username: string | null
  /** set when the session ended by itself (expired, revoked from another device) */
  expired: boolean
  login: (username: string, password: string, remember: boolean) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [username, setUsername] = useState<string | null>(null)
  const [expired, setExpired] = useState(false)

  const signedOut = useCallback(
    (byItself: boolean) => {
      queryClient.clear()
      setUsername(null)
      setStatus('anonymous')
      setExpired(byItself)
    },
    [queryClient],
  )

  useEffect(() => {
    let cancelled = false
    auth
      .me()
      .then((me) => {
        if (cancelled) return
        setUsername(me.username ?? 'api')
        setStatus('authenticated')
      })
      // 401, or hub unreachable : the login page says so when the user tries
      .catch(() => {
        if (!cancelled) setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => signedOut(true))
    return () => setUnauthorizedHandler(null)
  }, [signedOut])

  const login = useCallback(async (user: string, password: string, remember: boolean) => {
    const answer = await auth.login(user, password, remember)
    setUsername(answer.username)
    setExpired(false)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await auth.logout()
    } finally {
      signedOut(false)
    }
  }, [signedOut])

  const value = useMemo(
    () => ({ status, username, expired, login, logout }),
    [status, username, expired, login, logout],
  )
  return <AuthContext value={value}>{children}</AuthContext>
}

// eslint-disable-next-line react/only-export-components
export function useAuth() {
  const context = use(AuthContext)
  if (!context) throw new Error('useAuth outside of AuthProvider')
  return context
}
