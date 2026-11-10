export type MaterialListQuery = {
  page: number
  size: number
  q: string
  status: string
  sort: string
}

export function pageSummary(total: number, selected: number): string {
  return `共 ${total} 条记录，已选 ${selected} 项`
}

export const DEFAULT_MATERIAL_QUERY: MaterialListQuery = {
  page: 1,
  size: 10,
  q: '',
  status: '',
  sort: 'code,asc',
}

const SORTABLE = new Set(['code', 'name', 'status', 'updatedAt'])

/** Only parameters the live `GET /api/v1/materials` handler accepts. */
export function toMaterialParams(
  query: MaterialListQuery,
): Record<string, string | number | undefined> {
  const params: Record<string, string | number | undefined> = {
    page: query.page,
    size: query.size,
    sort: query.sort || 'code,asc',
  }
  const keyword = query.q.trim()
  if (keyword) params.q = keyword
  if (query.status) params.status = query.status
  return params
}

/** Keep the pager on a page that still has rows after a delete or a shorter result. */
export function resolvePage(page: number, size: number, total: number): number {
  const safeSize = size < 1 ? 1 : size
  const last = Math.max(1, Math.ceil(Math.max(0, total) / safeSize))
  if (page < 1) return 1
  return Math.min(page, last)
}

export function materialSort(field: unknown, order: unknown): string {
  if (typeof field !== 'string' || !SORTABLE.has(field)) return 'code,asc'
  if (order === 'descend') return `${field},desc`
  if (order === 'ascend') return `${field},asc`
  return 'code,asc'
}
