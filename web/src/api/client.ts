/** Every request goes to the same origin under /api (Caddy in production, the Vite proxy in development). */
const BASE = '/api'

export class ApiError extends Error {
  readonly status: number
  readonly retryAfter?: number

  constructor(status: number, message: string, retryAfter?: number) {
    super(message)
    this.status = status
    this.retryAfter = retryAfter
  }
}

let onUnauthorized: (() => void) | null = null

/** Called when the session is over (401 on an authenticated request). */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  /** false for the login requests : a 401 there is a wrong password, not an expired session */
  handleUnauthorized?: boolean
}

export async function api<T>(path: string, { method = 'GET', body, handleUnauthorized = true }: RequestOptions = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(BASE + path, {
      method,
      credentials: 'same-origin',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Impossible de joindre JARVIS')
  }

  const text = await response.text()
  let data: unknown = undefined
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = undefined
    }
  }

  if (!response.ok) {
    if (response.status === 401 && handleUnauthorized) onUnauthorized?.()
    const message =
      data && typeof data === 'object' && 'error' in data && typeof data.error === 'string'
        ? data.error
        : defaultMessage(response.status)
    const retryAfter = Number(response.headers.get('Retry-After')) || undefined
    throw new ApiError(response.status, message, retryAfter)
  }
  return data as T
}

function defaultMessage(status: number) {
  if (status === 401) return 'Session expirée'
  if (status === 404) return 'Introuvable'
  if (status >= 500) return 'Erreur du hub JARVIS'
  return 'Requête refusée'
}

export function query(params: Record<string, string | number | boolean>) {
  return '?' + new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString()
}
