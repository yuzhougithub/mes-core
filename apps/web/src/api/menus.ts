import { apiRequest } from './http.ts'

export type MenuType = 'DIR' | 'MENU' | 'BUTTON'

export type MenuNode = {
  id: string
  parentId: string | null
  type: MenuType
  name: string
  permCode: string | null
  path: string | null
  component: string | null
  icon: string | null
  sortNo: number
  visible: boolean
  enabled: boolean
  lockVersion: number
  createdAt?: string
  updatedAt?: string
  children: MenuNode[]
}

export type MenuWrite = {
  parentId: string | null
  type: string
  name: string
  permCode: string | null
  path: string | null
  component: string | null
  icon: string | null
  sortNo: number
  visible: boolean
  enabled: boolean
  lockVersion?: number
}

export async function menuTree(): Promise<MenuNode[]> {
  const body = await apiRequest<unknown>('/api/v1/menus/tree')
  return normalizeMenuTree(body)
}

export function createMenu(body: MenuWrite) {
  return apiRequest<MenuNode>('/api/v1/menus', { method: 'POST', body }).then(normalizeMenuNode)
}

export function updateMenu(id: string, body: MenuWrite) {
  return apiRequest<MenuNode>(`/api/v1/menus/${id}`, { method: 'PUT', body }).then(normalizeMenuNode)
}

export function deleteMenu(id: string, lockVersion: number) {
  return apiRequest<void>(`/api/v1/menus/${id}`, {
    method: 'DELETE',
    query: { lockVersion },
  })
}

function normalizeMenuNode(value: unknown): MenuNode {
  const node = readMenu(value)
  if (!node) {
    return {
      id: '',
      parentId: null,
      type: 'MENU',
      name: '',
      permCode: null,
      path: null,
      component: null,
      icon: null,
      sortNo: 0,
      visible: true,
      enabled: true,
      lockVersion: 0,
      children: [],
    }
  }
  return node
}

export function normalizeMenuTree(value: unknown): MenuNode[] {
  if (!Array.isArray(value)) return []
  const nodes: MenuNode[] = []
  for (const item of value) {
    const node = readMenu(item)
    if (node) nodes.push(node)
  }
  return nodes
}

function readMenu(value: unknown): MenuNode | null {
  if (!value || typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  if (typeof record.id !== 'string' || record.id === '') return null
  const type = record.type
  if (type !== 'DIR' && type !== 'MENU' && type !== 'BUTTON') return null
  const name = typeof record.name === 'string' ? record.name : ''
  return {
    id: record.id,
    parentId: typeof record.parentId === 'string' && record.parentId !== '' ? record.parentId : null,
    type,
    name,
    permCode: text(record.permCode),
    path: text(record.path),
    component: text(record.component),
    icon: text(record.icon),
    sortNo: typeof record.sortNo === 'number' ? record.sortNo : 0,
    visible: record.visible !== false,
    enabled: record.enabled !== false,
    lockVersion: typeof record.lockVersion === 'number' ? record.lockVersion : 0,
    createdAt: typeof record.createdAt === 'string' ? record.createdAt : undefined,
    updatedAt: typeof record.updatedAt === 'string' ? record.updatedAt : undefined,
    children: normalizeMenuTree(record.children),
  }
}

function text(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}
