import type { TokenResponse } from './types.ts'

const CSRF_HEADER = 'X-CSRF-Token'
const CSRF_COOKIES = ['csrf_token', 'XSRF-TOKEN', 'csrfToken']

let accessToken: string | null = null
let refreshInflight: Promise<TokenResponse | null> | null = null

const lostListeners = new Set<() => void>()
const refreshedListeners = new Set<(token: TokenResponse) => void>()
const passwordChangeListeners = new Set<() => void>()

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function onSessionLost(listener: () => void): () => void {
  lostListeners.add(listener)
  return () => {
    lostListeners.delete(listener)
  }
}

export function onSessionRefreshed(listener: (token: TokenResponse) => void): () => void {
  refreshedListeners.add(listener)
  return () => {
    refreshedListeners.delete(listener)
  }
}

export function onPasswordChangeRequired(listener: () => void): () => void {
  passwordChangeListeners.add(listener)
  return () => {
    passwordChangeListeners.delete(listener)
  }
}

export function emitPasswordChangeRequired(): void {
  for (const listener of passwordChangeListeners) listener()
}

/** Header method required on cookie-authenticated refresh and logout. A readable CSRF cookie is forwarded too. */
export function csrfHeaders(base?: Headers): Headers {
  const headers = base ?? new Headers()
  headers.set('Accept', 'application/json, application/problem+json')
  headers.set('X-Requested-With', 'XMLHttpRequest')
  const csrf = readCsrfCookie()
  if (csrf) headers.set(CSRF_HEADER, csrf)
  return headers
}

export function readCsrfCookie(): string | null {
  if (typeof document === 'undefined') return null
  const parts = document.cookie.split(';')
  for (const part of parts) {
    const trimmed = part.trim()
    const eq = trimmed.indexOf('=')
    if (eq <= 0) continue
    const name = trimmed.slice(0, eq)
    if (!CSRF_COOKIES.includes(name)) continue
    const value = trimmed.slice(eq + 1)
    if (!value) continue
    try {
      return decodeURIComponent(value)
    } catch {
      return value
    }
  }
  return null
}

export function refreshAccess(): Promise<TokenResponse | null> {
  if (!refreshInflight) {
    refreshInflight = refreshOnce().finally(() => {
      refreshInflight = null
    })
  }
  return refreshInflight
}

async function refreshOnce(): Promise<TokenResponse | null> {
  let response: Response
  try {
    response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
      headers: csrfHeaders(),
    })
  } catch {
    endSession()
    return null
  }
  if (!response.ok) {
    endSession()
    return null
  }
  let body: unknown = null
  try {
    body = await response.json()
  } catch {
    endSession()
    return null
  }
  const token = parseToken(body)
  if (!token) {
    endSession()
    return null
  }
  accessToken = token.accessToken
  for (const listener of refreshedListeners) listener(token)
  return token
}

export function endSession(): void {
  accessToken = null
  for (const listener of lostListeners) listener()
}

export async function logoutRequest(): Promise<void> {
  const token = accessToken
  const headers = csrfHeaders()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  try {
    await fetch('/api/v1/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers,
    })
  } catch {
    /* The local session still ends. */
  }
  accessToken = null
}

export function parseToken(body: unknown): TokenResponse | null {
  if (!body || typeof body !== 'object') return null
  const record = body as Record<string, unknown>
  if (typeof record.accessToken !== 'string' || record.accessToken === '') return null
  return {
    accessToken: record.accessToken,
    expiresIn: typeof record.expiresIn === 'number' ? record.expiresIn : 0,
    mustChangePassword: record.mustChangePassword === true,
    plant: parsePlant(record.plant),
    roles: Array.isArray(record.roles)
      ? record.roles.filter((item): item is string => typeof item === 'string')
      : [],
  }
}

function parsePlant(value: unknown): TokenResponse['plant'] {
  if (!value || typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  if (typeof record.id !== 'string' || record.id === '') return null
  return {
    id: record.id,
    code: typeof record.code === 'string' ? record.code : '',
    name: typeof record.name === 'string' ? record.name : '',
  }
}
