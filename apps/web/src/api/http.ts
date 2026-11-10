import {
  ApiError,
  StalePlantResponse,
  parseProblem,
} from './problem.ts'
import {
  beginPlantRequest,
  endPlantRequest,
  getPlantEpoch,
  getPlantId,
} from './plant-session.ts'

export type QueryValue = string | number | boolean | null | undefined

export type RequestOptions = {
  method?: string
  query?: Record<string, QueryValue>
  body?: unknown
  /**
   * Send `X-Plant-Id` and drop the response if the factory changed.
   * Global `/plants` sets this to false.
   */
  plant?: boolean
  /** Explicit factory. Mismatch with the session aborts before fetch. */
  plantId?: string
  signal?: AbortSignal
}

export function withQuery(path: string, query: RequestOptions['query']): string {
  if (!query) return path
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    params.set(key, String(value))
  }
  const text = params.toString()
  return text ? `${path}?${text}` : path
}

function plantChanged(epochAtStart: number, pinned: string | null, usePlant: boolean): boolean {
  if (!usePlant) return false
  return getPlantEpoch() !== epochAtStart || getPlantId() !== pinned
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const usePlant = options.plant !== false
  const pinned = usePlant ? (options.plantId ?? getPlantId()) : null
  const epochAtStart = getPlantEpoch()
  if (usePlant && !pinned) {
    throw new ApiError({
      title: 'Bad Request',
      status: 400,
      detail: '请先选择工厂',
      code: 'PLANT_REQUIRED',
      errors: [],
    })
  }
  if (plantChanged(epochAtStart, pinned, usePlant)) {
    throw new StalePlantResponse()
  }

  const controller = usePlant ? beginPlantRequest(epochAtStart) : new AbortController()
  if (options.signal) {
    if (options.signal.aborted) controller.abort()
    else options.signal.addEventListener('abort', () => controller.abort(), { once: true })
  }

  const headers = new Headers()
  headers.set('Accept', 'application/json, application/problem+json')
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (import.meta.env.DEV && usePlant && pinned) headers.set('X-Plant-Id', pinned)

  let response: Response
  try {
    response = await fetch(withQuery(path, options.query), {
      method: options.method ?? (options.body !== undefined ? 'POST' : 'GET'),
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: controller.signal,
    })
  } catch (err) {
    if (plantChanged(epochAtStart, pinned, usePlant)) throw new StalePlantResponse()
    throw err
  } finally {
    if (usePlant) endPlantRequest(controller)
  }

  if (plantChanged(epochAtStart, pinned, usePlant)) throw new StalePlantResponse()

  if (response.status === 204) return undefined as T

  if (!isJsonBody(response)) {
    if (!response.ok) throw unavailable(response)
    return undefined as T
  }

  const text = await response.text()
  let parsed: unknown = null
  if (text) {
    try {
      parsed = JSON.parse(text)
    } catch {
      if (!response.ok) throw unavailable(response)
      return undefined as T
    }
  }
  if (!response.ok) {
    throw new ApiError(parseProblem(response.status, parsed, response.headers.get('X-Trace-Id')))
  }
  return parsed as T
}

function isJsonBody(response: Response): boolean {
  const media = (response.headers.get('Content-Type') ?? '').split(';')[0]?.trim().toLowerCase()
  return media === 'application/json' || media === 'application/problem+json'
}

function unavailable(response: Response): ApiError {
  return new ApiError({
    title: '服务暂不可用',
    status: response.status,
    detail: `服务暂不可用（HTTP ${response.status}）`,
    code: `HTTP_${response.status}`,
    errors: [],
    traceId: response.headers.get('X-Trace-Id') ?? undefined,
  })
}
