import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './http.ts'
import { ApiError, StalePlantResponse } from './problem.ts'
import { getPlantId, resetPlantSession, setActivePlant } from './plant-session.ts'

afterEach(() => {
  resetPlantSession()
  vi.unstubAllGlobals()
})

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

describe('apiRequest', () => {
  it('sends X-Plant-Id for plant-scoped calls and omits it for plants', async () => {
    setActivePlant('plant-a')
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const headers = new Headers(init?.headers)
      const url = String(_input)
      if (url.startsWith('/api/v1/plants')) {
        expect(headers.get('X-Plant-Id')).toBeNull()
        return jsonResponse({ items: [], page: 1, size: 20, total: 0 })
      }
      expect(headers.get('X-Plant-Id')).toBe('plant-a')
      return jsonResponse({ items: [], page: 1, size: 20, total: 0 })
    })
    vi.stubGlobal('fetch', fetchMock)
    await apiRequest('/api/v1/materials', { query: { page: 1, size: 20 } })
    await apiRequest('/api/v1/plants', { plant: false })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(getPlantId()).toBe('plant-a')
  })

  it('parses problem details and keeps trace id', async () => {
    setActivePlant('plant-a')
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      type: 'about:blank',
      title: '校验失败',
      status: 422,
      detail: '1 个字段不合法',
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'values.rohs', code: 'REQUIRED', message: 'RoHS必填' }],
      traceId: 'abc',
    }), {
      status: 422,
      headers: {
        'Content-Type': 'application/problem+json',
        'X-Trace-Id': 'from-header',
      },
    })))
    await expect(apiRequest('/api/v1/materials')).rejects.toBeInstanceOf(ApiError)
    try {
      await apiRequest('/api/v1/materials')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      if (err instanceof ApiError) {
        expect(err.problem.code).toBe('VALIDATION_FAILED')
        expect(err.problem.errors[0]?.field).toBe('values.rohs')
        expect(err.problem.traceId).toBe('abc')
      }
    }
  })

  it('drops a response that arrives after the factory changes', async () => {
    setActivePlant('plant-a')
    vi.stubGlobal('fetch', vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20))
      return jsonResponse({ items: [{ code: 'FROM-A' }], page: 1, size: 20, total: 1 })
    }))
    const pending = apiRequest<{ items: { code: string }[] }>('/api/v1/materials')
    setActivePlant('plant-b')
    await expect(pending).rejects.toBeInstanceOf(StalePlantResponse)
  })

  it('omits X-Plant-Id when the build is not DEV', async () => {
    vi.stubEnv('DEV', false)
    setActivePlant('plant-a')
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      expect(new Headers(init?.headers).get('X-Plant-Id')).toBeNull()
      return jsonResponse({ items: [], page: 1, size: 20, total: 0 })
    })
    vi.stubGlobal('fetch', fetchMock)
    await apiRequest('/api/v1/materials')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('hides non-json error bodies behind a fixed message', async () => {
    setActivePlant('plant-a')
    vi.stubGlobal('fetch', vi.fn(async () => new Response('<html>upstream secret</html>', {
      status: 502,
      headers: { 'Content-Type': 'text/html' },
    })))
    await expect(apiRequest('/api/v1/materials')).rejects.toMatchObject({
      problem: { detail: '服务暂不可用（HTTP 502）', status: 502 },
    })
    try {
      await apiRequest('/api/v1/materials')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      if (err instanceof ApiError) {
        expect(err.problem.detail).not.toContain('secret')
        expect(err.problem.detail).not.toContain('<html>')
      }
    }
  })

  it('refuses plant calls before a factory is selected', async () => {
    await expect(apiRequest('/api/v1/materials')).rejects.toMatchObject({
      problem: { code: 'PLANT_REQUIRED' },
    })
  })
})
