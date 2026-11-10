import type { MeMenu } from './types.ts'

const TYPES = new Set(['DIR', 'MENU', 'BUTTON'])

/** Keep only the menu tree `/auth/me` actually returned. */
export function normalizeMenus(value: unknown): MeMenu[] {
  if (!Array.isArray(value)) return []
  const menus: MeMenu[] = []
  for (const item of value) {
    const menu = normalizeMenu(item)
    if (menu) menus.push(menu)
  }
  return menus
}

function normalizeMenu(value: unknown): MeMenu | null {
  if (!value || typeof value !== 'object') return null
  const record = value as Record<string, unknown>
  if (typeof record.id !== 'string' || record.id === '') return null
  if (typeof record.name !== 'string' || record.name.trim() === '') return null
  if (typeof record.type !== 'string' || !TYPES.has(record.type)) return null
  return {
    id: record.id,
    parentId: typeof record.parentId === 'string' && record.parentId !== '' ? record.parentId : null,
    type: record.type as MeMenu['type'],
    name: record.name.trim(),
    permCode: typeof record.permCode === 'string' && record.permCode !== '' ? record.permCode : null,
    path: typeof record.path === 'string' && record.path !== '' ? record.path : null,
    component: typeof record.component === 'string' && record.component !== '' ? record.component : null,
    icon: typeof record.icon === 'string' && record.icon !== '' ? record.icon : null,
    sortNo: typeof record.sortNo === 'number' ? record.sortNo : 0,
    visible: record.visible !== false,
    children: normalizeMenus(record.children),
  }
}
