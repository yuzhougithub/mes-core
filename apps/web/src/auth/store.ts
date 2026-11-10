import { apiRequest } from '../api/http.ts'
import { ApiError, parseProblem } from '../api/problem.ts'
import { setActivePlant } from '../api/plant-session.ts'
import {
  getAccessToken,
  logoutRequest,
  onPasswordChangeRequired,
  onSessionLost,
  onSessionRefreshed,
  parseToken,
  refreshAccess,
  setAccessToken,
} from './session.ts'
import {
  NO_PLANT_ID,
  type AuthSnapshot,
  type MePerson,
  type MePlant,
  type MeResponse,
  type MeUser,
  type PlantOption,
} from './types.ts'

const listeners = new Set<() => void>()

let snapshot: AuthSnapshot = {
  phase: 'booting',
  token: null,
  me: null,
  notice: null,
}

let pendingUsername = ''

export function getPendingUsername(): string {
  return pendingUsername
}

function emit(): void {
  for (const listener of listeners) listener()
}

export function getAuthSnapshot(): AuthSnapshot {
  return snapshot
}

export function subscribeAuth(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function usablePlantId(id: string | null | undefined): string | null {
  if (!id || id === NO_PLANT_ID) return null
  return id
}

function publish(next: AuthSnapshot): void {
  snapshot = next
  emit()
}

function applyMe(me: MeResponse, token: string | null): void {
  const plantId = usablePlantId(me.plant?.id)
  const phase = me.user.mustChangePassword ? 'must-change' : plantId ? 'ready' : 'no-plant'
  if (plantId) setActivePlant(plantId)
  else setActivePlant(null)
  publish({ phase, token, me: { ...me, plant: plantId ? me.plant : null }, notice: null })
}

onSessionLost(() => {
  setActivePlant(null)
  publish({ phase: 'anonymous', token: null, me: null, notice: null })
})

onPasswordChangeRequired(() => {
  publish({
    phase: 'must-change',
    token: getAccessToken(),
    me: snapshot.me,
    notice: null,
  })
})

onSessionRefreshed((token) => {
  setAccessToken(token.accessToken)
  if (token.mustChangePassword) {
    publish({ phase: 'must-change', token: token.accessToken, me: snapshot.me, notice: null })
    return
  }
  if (snapshot.phase === 'booting') {
    snapshot = { ...snapshot, token: token.accessToken }
    return
  }
  const plantId = usablePlantId(token.plant?.id)
  if (snapshot.me && plantId && snapshot.phase === 'ready') {
    setActivePlant(plantId)
    publish({
      ...snapshot,
      token: token.accessToken,
      me: {
        ...snapshot.me,
        plant: { id: plantId, code: token.plant?.code ?? null, name: token.plant?.name ?? null },
        roles: token.roles,
        user: { ...snapshot.me.user, mustChangePassword: false },
      },
    })
    return
  }
  snapshot = { ...snapshot, token: token.accessToken }
  emit()
})

export async function restoreSession(isCancelled: () => boolean): Promise<void> {
  if (snapshot.phase !== 'booting') return
  const token = await refreshAccess()
  if (isCancelled() || snapshot.phase !== 'booting') return
  if (!token) {
    publish({ phase: 'anonymous', token: null, me: null, notice: null })
    return
  }
  if (token.mustChangePassword) {
    publish({ phase: 'must-change', token: token.accessToken, me: null, notice: null })
    return
  }
  try {
    const me = await fetchMe()
    const phase = getAuthSnapshot().phase
    if (isCancelled() || phase === 'anonymous' || phase === 'must-change') return
    applyMe(me, token.accessToken)
  } catch {
    if (isCancelled()) return
    setAccessToken(null)
    setActivePlant(null)
    publish({ phase: 'anonymous', token: null, me: null, notice: null })
  }
}

export async function login(username: string, password: string): Promise<void> {
  pendingUsername = username.trim().toLowerCase()
  let response: Response
  try {
    response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json, application/problem+json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    })
  } catch (err) {
    const detail = err instanceof Error ? err.message : '请求失败'
    throw new ApiError({ title: '网络错误', status: 0, detail, code: 'NETWORK', errors: [] })
  }
  const parsed = await readBody(response)
  if (!response.ok) {
    throw new ApiError(parseProblem(response.status, parsed, response.headers.get('X-Trace-Id')))
  }
  const token = parseToken(parsed)
  if (!token) {
    throw new ApiError({
      title: '登录失败',
      status: response.status,
      detail: '登录响应缺少访问令牌',
      code: 'TOKEN_INVALID',
      errors: [],
    })
  }
  setAccessToken(token.accessToken)
  if (token.mustChangePassword) {
    publish({ phase: 'must-change', token: token.accessToken, me: null, notice: null })
    return
  }
  try {
    const me = await fetchMe()
    if (getAuthSnapshot().phase === 'must-change') return
    if (getAccessToken() !== token.accessToken) {
      throw new ApiError({
        title: '登录失败',
        status: 401,
        detail: '登录状态已失效',
        code: 'UNAUTHENTICATED',
        errors: [],
      })
    }
    applyMe(me, token.accessToken)
  } catch (err) {
    if (getAuthSnapshot().phase === 'must-change') return
    if (getAccessToken() === token.accessToken) setAccessToken(null)
    throw err
  }
}

export async function logout(): Promise<void> {
  await logoutRequest()
  setActivePlant(null)
  publish({ phase: 'anonymous', token: null, me: null, notice: null })
}

export function finishPasswordChange(): void {
  setAccessToken(null)
  setActivePlant(null)
  publish({ phase: 'anonymous', token: null, me: null, notice: '密码已更新，请重新登录' })
}

export async function reloadMe(): Promise<void> {
  const token = getAccessToken()
  if (!token) return
  const me = await fetchMe()
  applyMe(me, token)
}

export async function switchPlant(plantId: string): Promise<void> {
  const raw = await apiRequest<unknown>('/api/v1/auth/switch-plant', {
    method: 'POST',
    body: { plantId },
    plant: true,
  })
  const token = parseToken(raw)
  if (!token) {
    throw new ApiError({
      title: '切换工厂失败',
      status: 500,
      detail: '响应缺少访问令牌',
      code: 'TOKEN_INVALID',
      errors: [],
    })
  }
  setAccessToken(token.accessToken)
  setActivePlant(plantId)
  if (!snapshot.me || !token.plant) return
  publish({
    ...snapshot,
    token: token.accessToken,
    phase: 'ready',
    me: {
      ...snapshot.me,
      plant: { id: token.plant.id, code: token.plant.code, name: token.plant.name },
      roles: token.roles,
    },
  })
}

/** After a no-plant admin assigns themselves, refresh replaces the sentinel token. */
export async function recoverPlantAccess(): Promise<void> {
  const token = await refreshAccess()
  if (!token || snapshot.phase === 'must-change' || snapshot.phase === 'anonymous') return
  const me = await fetchMe()
  applyMe(me, getAccessToken() ?? token.accessToken)
}

export async function fetchMe(): Promise<MeResponse> {
  const raw = await apiRequest<unknown>('/api/v1/auth/me', { plant: false })
  return normalizeMe(raw)
}

export function seedAuthSession(next: AuthSnapshot): void {
  snapshot = next
  setAccessToken(next.token)
  const plantId = next.phase === 'ready' ? usablePlantId(next.me?.plant?.id) : null
  if (plantId) setActivePlant(plantId)
  emit()
}

export function resetAuthSession(): void {
  snapshot = { phase: 'booting', token: null, me: null, notice: null }
  setAccessToken(null)
  emit()
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}

function normalizeMe(body: unknown): MeResponse {
  const record = body && typeof body === 'object' ? (body as Record<string, unknown>) : {}
  const user = record.user && typeof record.user === 'object' ? (record.user as Record<string, unknown>) : {}
  const plant = normalizePlant(record.plant)
  return {
    user: normalizeUser(user),
    person: normalizePerson(record.person),
    plant: usablePlantId(plant?.id) ? plant : null,
    plants: Array.isArray(record.plants) ? record.plants.map(normalizeOption).filter((item) => item.id !== '') : [],
    roles: stringList(record.roles),
    permissions: stringList(record.permissions),
    menus: Array.isArray(record.menus) ? record.menus : [],
  }
}

function normalizeUser(user: Record<string, unknown>): MeUser {
  return {
    id: typeof user.id === 'string' ? user.id : null,
    username: typeof user.username === 'string' ? user.username : null,
    status: typeof user.status === 'string' ? user.status : null,
    superAdmin: user.superAdmin === true,
    mustChangePassword: user.mustChangePassword === true,
    permVersion: typeof user.permVersion === 'number' ? user.permVersion : null,
  }
}

function normalizePerson(value: unknown): MePerson | null {
  if (!value || typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  if (typeof record.id !== 'string' || record.id === '') return null
  return {
    id: record.id,
    code: typeof record.code === 'string' ? record.code : null,
    name: typeof record.name === 'string' ? record.name : null,
  }
}

function normalizePlant(value: unknown): MePlant | null {
  if (!value || typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  if (typeof record.id !== 'string' || record.id === '') return null
  return {
    id: record.id,
    code: typeof record.code === 'string' ? record.code : null,
    name: typeof record.name === 'string' ? record.name : null,
  }
}

function normalizeOption(value: unknown): PlantOption {
  if (!value || typeof value !== 'object') return { id: '', code: null, name: null, isDefault: false }
  const record = value as Record<string, unknown>
  const flagged = record.isDefault === true || record.defaultPlant === true
  return {
    id: typeof record.id === 'string' ? record.id : '',
    code: typeof record.code === 'string' ? record.code : null,
    name: typeof record.name === 'string' ? record.name : null,
    isDefault: flagged,
  }
}

function stringList(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === 'string')
}
