export type RoleListQuery = {
  page: number
  size: number
  q: string
  sort: string
}

export const DEFAULT_ROLE_QUERY: RoleListQuery = {
  page: 1,
  size: 10,
  q: '',
  sort: 'sortNo,asc',
}

const SORTABLE = new Set(['code', 'name', 'sortNo', 'createdAt'])

export function toRoleParams(query: RoleListQuery): Record<string, string | number | undefined> {
  const params: Record<string, string | number | undefined> = {
    page: query.page,
    size: query.size,
    sort: query.sort || 'sortNo,asc',
  }
  const keyword = query.q.trim()
  if (keyword) params.q = keyword
  return params
}

export function roleSort(field: unknown, order: unknown): string {
  if (typeof field !== 'string' || !SORTABLE.has(field)) return 'sortNo,asc'
  if (order === 'descend') return `${field},desc`
  if (order === 'ascend') return `${field},asc`
  return 'sortNo,asc'
}
