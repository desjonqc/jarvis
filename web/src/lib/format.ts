import { ApiError } from '../api/client'

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return 'Erreur inattendue'
}

const relative = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' })

/** "il y a 3 min", "hier"... */
export function timeAgo(timestamp: number, now = Date.now()): string {
  const seconds = Math.round((timestamp - now) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 45) return "à l'instant"
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute')
  if (abs < 86_400) return relative.format(Math.round(seconds / 3600), 'hour')
  if (abs < 30 * 86_400) return relative.format(Math.round(seconds / 86_400), 'day')
  return dateTime(timestamp)
}

const dateTimeFormat = new Intl.DateTimeFormat('fr', { dateStyle: 'medium', timeStyle: 'short' })

export function dateTime(timestamp: number): string {
  return dateTimeFormat.format(timestamp)
}

export function hour(value: number): string {
  return `${String(value).padStart(2, '0')}:00`
}

/** "Safari sur iPhone" from a user agent. */
export function describeDevice(userAgent: string): string {
  if (!userAgent) return 'Appareil inconnu'
  const os = /iPhone/.test(userAgent)
    ? 'iPhone'
    : /iPad/.test(userAgent)
      ? 'iPad'
      : /Android/.test(userAgent)
        ? 'Android'
        : /Mac OS X|Macintosh/.test(userAgent)
          ? 'Mac'
          : /Windows/.test(userAgent)
            ? 'Windows'
            : /Linux/.test(userAgent)
              ? 'Linux'
              : null
  const browser = /Edg\//.test(userAgent)
    ? 'Edge'
    : /Firefox\//.test(userAgent)
      ? 'Firefox'
      : /Chrome\//.test(userAgent)
        ? 'Chrome'
        : /Safari\//.test(userAgent)
          ? 'Safari'
          : /curl|Java|python/i.test(userAgent)
            ? 'Script'
            : 'Navigateur'
  return os ? `${browser} sur ${os}` : browser
}
