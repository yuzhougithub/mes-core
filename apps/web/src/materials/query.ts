export type MaterialListQuery = {
  page: number
  size: number
  q: string
  status: string
  sort: string
}

export const DEFAULT_MATERIAL_QUERY: MaterialListQuery = {
  page: 1,
  size: 20,
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

export function materialSort(field: unknown, order: unknown): string {
  if (typeof field !== 'string' || !SORTABLE.has(field)) return 'code,asc'
  if (order === 'descend') return `${field},desc`
  if (order === 'ascend') return `${field},asc`
  return 'code,asc'
}
