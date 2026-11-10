/** RFC 9457 Problem Details plus the repo extensions `code`, `errors[]`, `traceId`. */

export type FieldProblem = {
  field: string
  code: string
  message: string
}

export type ProblemDetails = {
  title: string
  status: number
  detail: string
  code: string
  errors: FieldProblem[]
  traceId?: string
  instance?: string
}

export class ApiError extends Error {
  readonly problem: ProblemDetails

  constructor(problem: ProblemDetails) {
    super(problem.detail || problem.title)
    this.name = 'ApiError'
    this.problem = problem
  }
}

/** In-flight work from another factory must not be applied or continued. */
export class StalePlantResponse extends Error {
  constructor() {
    super('stale plant response')
    this.name = 'StalePlantResponse'
  }
}

export function isStale(err: unknown): boolean {
  if (err instanceof StalePlantResponse) return true
  return err instanceof DOMException && err.name === 'AbortError'
}

/** Forced password change replaces the shell. A 401 is shown, not dropped. */
export function isAuthRedirect(err: unknown): boolean {
  return err instanceof ApiError && err.problem.code === 'PASSWORD_CHANGE_REQUIRED'
}

export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err
  const detail = err instanceof Error ? err.message : '请求失败'
  return new ApiError({
    title: '网络错误',
    status: 0,
    detail,
    code: 'NETWORK',
    errors: [],
  })
}

export function parseProblem(
  status: number,
  body: unknown,
  traceHeader: string | null,
): ProblemDetails {
  const record = body && typeof body === 'object' ? (body as Record<string, unknown>) : {}
  const errors: FieldProblem[] = []
  if (Array.isArray(record.errors)) {
    for (const item of record.errors) {
      if (!item || typeof item !== 'object') continue
      const entry = item as Record<string, unknown>
      errors.push({
        field: typeof entry.field === 'string' ? entry.field : '',
        code: typeof entry.code === 'string' ? entry.code : '',
        message: typeof entry.message === 'string' ? entry.message : '',
      })
    }
  }
  const bodyTrace = typeof record.traceId === 'string' ? record.traceId : ''
  return {
    title: typeof record.title === 'string' && record.title ? record.title : '请求失败',
    status: typeof record.status === 'number' ? record.status : status,
    detail: typeof record.detail === 'string' ? record.detail : '',
    code: typeof record.code === 'string' && record.code ? record.code : `HTTP_${status}`,
    errors,
    traceId: bodyTrace || traceHeader || undefined,
    instance: typeof record.instance === 'string' ? record.instance : undefined,
  }
}

export function formatProblem(problem: ProblemDetails): string {
  const lines = [`${problem.title}（${problem.code}）`]
  if (problem.detail) lines.push(problem.detail)
  for (const error of problem.errors) {
    const where = error.field ? `${error.field}：` : ''
    lines.push(`${where}${error.message}${error.code ? `（${error.code}）` : ''}`)
  }
  if (problem.traceId) lines.push(`traceId ${problem.traceId}`)
  return lines.join('\n')
}
