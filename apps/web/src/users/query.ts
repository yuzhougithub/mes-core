export type UserListQuery = {
  page: number
  size: number
  q: string
  status: string
  sort: string
}

export const DEFAULT_USER_QUERY: UserListQuery = {
  page: 1,
  size: 10,
  q: '',
  status: '',
  sort: 'username,asc',
}

const SORTABLE = new Set(['username', 'createdAt', 'updatedAt'])

export function toUserParams(query: UserListQuery): Record<string, string | number | undefined> {
  const params: Record<string, string | number | undefined> = {
    page: query.page,
    size: query.size,
    sort: query.sort || 'username,asc',
  }
  const keyword = query.q.trim()
  if (keyword) params.q = keyword
  if (query.status) params.status = query.status
  return params
}

export function userSort(field: unknown, order: unknown): string {
  if (typeof field !== 'string' || !SORTABLE.has(field)) return 'username,asc'
  if (order === 'descend') return `${field},desc`
  if (order === 'ascend') return `${field},asc`
  return 'username,asc'
}
