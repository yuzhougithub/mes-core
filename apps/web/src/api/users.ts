import { getPlantId } from './plant-session.ts'
import { apiRequest } from './http.ts'
import type { PageResult } from './types.ts'

export type UserStatus = 'ACTIVE' | 'DISABLED'

export type UserSummary = {
  id: string
  username: string
  status: string
  superAdmin: boolean
  mustChangePassword: boolean
  personId: string | null
  personCode: string | null
  personName: string | null
  defaultPlantId: string | null
  lockVersion: number
  createdAt?: string
  updatedAt?: string
  mobile?: string | null
  email?: string | null
  person?: {
    mobile?: string | null
    email?: string | null
  } | null
}

export type PlantBinding = {
  plantId: string
  plantCode?: string | null
  plantName?: string | null
  isDefault: boolean
  active: boolean
  workshopId?: string | null
  jobTitle?: string | null
  roleIds?: string[]
  lockVersion?: number
}

export type UserDetail = {
  id: string
  username: string
  status: string
  superAdmin: boolean
  mustChangePassword: boolean
  permVersion: number
  defaultPlantId: string | null
  lockVersion: number
  person: {
    id: string
    code: string
    name: string
    mobile: string | null
    email: string | null
    enabled: boolean
    remark: string | null
    lockVersion: number
  }
  plants: PlantBinding[]
  createdAt?: string
  updatedAt?: string
  initialPassword?: string | null
}

export type PasswordReset = {
  password?: string | null
  mustChangePassword: boolean
}

export type PlantBindingInput = {
  plantId: string
  isDefault: boolean
  active: boolean
  jobTitle?: string | null
  roleIds: string[]
}

export type UserWrite = {
  username?: string
  password?: string
  code: string
  name: string
  mobile?: string | null
  email?: string | null
  remark?: string | null
  superAdmin?: boolean
  plants: PlantBindingInput[]
}

export type UserUpdate = {
  username: string
  name: string
  mobile?: string | null
  email?: string | null
  remark?: string | null
  lockVersion: number
}

function scoped(): { plant: boolean } {
  return { plant: getPlantId() !== null }
}

export function listUsers(query: Record<string, string | number | undefined>) {
  return apiRequest<PageResult<UserSummary>>('/api/v1/users', { query, ...scoped() })
}

export function getUser(id: string) {
  return apiRequest<UserDetail>(`/api/v1/users/${id}`, scoped())
}

export function createUser(body: UserWrite) {
  return apiRequest<UserDetail>('/api/v1/users', { method: 'POST', body, ...scoped() })
}

export function updateUser(id: string, body: UserUpdate) {
  return apiRequest<UserDetail>(`/api/v1/users/${id}`, { method: 'PUT', body, ...scoped() })
}

export function deleteUser(id: string, lockVersion: number) {
  return apiRequest<void>(`/api/v1/users/${id}`, {
    method: 'DELETE',
    query: { lockVersion },
    ...scoped(),
  })
}

export function disableUser(id: string, lockVersion: number) {
  return apiRequest<UserDetail>(`/api/v1/users/${id}/disable`, {
    method: 'POST',
    body: { lockVersion },
    ...scoped(),
  })
}

export function enableUser(id: string, lockVersion: number) {
  return apiRequest<UserDetail>(`/api/v1/users/${id}/enable`, {
    method: 'POST',
    body: { lockVersion },
    ...scoped(),
  })
}

export function resetUserPassword(id: string, lockVersion: number, newPassword?: string) {
  const body: { lockVersion: number; newPassword?: string } = { lockVersion }
  if (newPassword) body.newPassword = newPassword
  return apiRequest<PasswordReset>(`/api/v1/users/${id}/reset-password`, {
    method: 'POST',
    body,
    ...scoped(),
  })
}

export function replaceUserPlants(id: string, lockVersion: number, plants: PlantBindingInput[]) {
  return apiRequest<UserDetail>(`/api/v1/users/${id}/plants`, {
    method: 'PUT',
    body: { lockVersion, plants },
    ...scoped(),
  })
}

export function userMobile(row: UserSummary): string | null {
  const value = row.mobile ?? row.person?.mobile ?? null
  if (value == null || value.trim() === '') return null
  return value
}

export function userEmail(row: UserSummary): string | null {
  const value = row.email ?? row.person?.email ?? null
  if (value == null || value.trim() === '') return null
  return value
}

export function contactKnown(row: UserSummary): boolean {
  return 'mobile' in row || 'email' in row || row.person != null
}
