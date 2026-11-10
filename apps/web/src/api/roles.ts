import { apiRequest } from './http.ts'
import type { PageResult } from './types.ts'

export type Role = {
  id: string
  code: string
  name: string
  remark: string | null
  dataScope: string
  sortNo: number
  enabled: boolean
  builtin: boolean
  lockVersion: number
  createdAt?: string
  updatedAt?: string
}

export type RoleCreate = {
  code: string
  name: string
  remark: string | null
  dataScope: string
  sortNo: number
  enabled: boolean
}

export type RoleUpdate = {
  name: string
  remark: string | null
  dataScope: string
  sortNo: number
  enabled: boolean
  lockVersion: number
}

export function listRoles(query: Record<string, string | number | undefined>) {
  return apiRequest<PageResult<Role>>('/api/v1/roles', { query })
}

export async function listAllRoles(): Promise<Role[]> {
  const items: Role[] = []
  let page = 1
  let total = 0
  do {
    const result = await listRoles({ page, size: 200, sort: 'sortNo,asc' })
    items.push(...(result.items ?? []))
    total = result.total ?? items.length
    page += 1
  } while (items.length < total && page <= 20)
  return items
}

export function createRole(body: RoleCreate) {
  return apiRequest<Role>('/api/v1/roles', { method: 'POST', body })
}

export function updateRole(id: string, body: RoleUpdate) {
  return apiRequest<Role>(`/api/v1/roles/${id}`, { method: 'PUT', body })
}

export function deleteRole(id: string, lockVersion: number) {
  return apiRequest<void>(`/api/v1/roles/${id}`, {
    method: 'DELETE',
    query: { lockVersion },
  })
}

export async function roleMenuIds(id: string): Promise<string[]> {
  const body = await apiRequest<unknown>(`/api/v1/roles/${id}/menus`)
  if (!body || typeof body !== 'object') return []
  const ids = (body as { menuIds?: unknown }).menuIds
  if (!Array.isArray(ids)) return []
  return ids.filter((item): item is string => typeof item === 'string')
}

export async function replaceRoleMenus(id: string, menuIds: string[]): Promise<string[]> {
  const body = await apiRequest<unknown>(`/api/v1/roles/${id}/menus`, {
    method: 'PUT',
    body: { menuIds },
  })
  if (!body || typeof body !== 'object') return []
  const ids = (body as { menuIds?: unknown }).menuIds
  if (!Array.isArray(ids)) return menuIds
  return ids.filter((item): item is string => typeof item === 'string')
}
