import type { MeMenu } from '../auth/types.ts'

export type NavLeaf = {
  kind: 'leaf'
  key: string
  label: string
  breadcrumb: string[]
  component: string | null
  path: string | null
  icon: string | null
}

export type NavDir = {
  kind: 'dir'
  key: string
  label: string
  icon: string | null
  children: NavNode[]
}

export type NavNode = NavLeaf | NavDir

export type NavModule = {
  key: string
  label: string
  icon: string | null
  nodes: NavNode[]
}

/** Sidebar and top modules come only from the menu tree on `/auth/me`. */
export function navFromMenus(menus: readonly MeMenu[]): NavModule[] {
  const modules: NavModule[] = []
  for (const menu of menus) {
    if (menu.type === 'BUTTON') continue
    if (menu.type === 'MENU') {
      modules.push({
        key: menu.id,
        label: menu.name,
        icon: menu.icon,
        nodes: [toLeaf(menu, [menu.name])],
      })
      continue
    }
    modules.push({
      key: menu.id,
      label: menu.name,
      icon: menu.icon,
      nodes: toNodes(menu.children, [menu.name]),
    })
  }
  return modules
}

export function firstLeaf(nodes: readonly NavNode[]): NavLeaf | null {
  for (const node of nodes) {
    if (node.kind === 'leaf') return node
    const child = firstLeaf(node.children)
    if (child) return child
  }
  return null
}

export function findLeaf(nodes: readonly NavNode[], key: string | null): NavLeaf | null {
  if (!key) return null
  for (const node of nodes) {
    if (node.kind === 'leaf') {
      if (node.key === key) return node
      continue
    }
    const child = findLeaf(node.children, key)
    if (child) return child
  }
  return null
}

export function dirKeys(nodes: readonly NavNode[]): string[] {
  const keys: string[] = []
  const walk = (rows: readonly NavNode[]) => {
    for (const node of rows) {
      if (node.kind !== 'dir') continue
      keys.push(node.key)
      walk(node.children)
    }
  }
  walk(nodes)
  return keys
}

function toNodes(menus: readonly MeMenu[], trail: string[]): NavNode[] {
  const nodes: NavNode[] = []
  for (const menu of menus) {
    if (menu.type === 'BUTTON') continue
    if (menu.type === 'DIR') {
      nodes.push({
        kind: 'dir',
        key: menu.id,
        label: menu.name,
        icon: menu.icon,
        children: toNodes(menu.children, [...trail, menu.name]),
      })
      continue
    }
    nodes.push(toLeaf(menu, [...trail, menu.name]))
  }
  return nodes
}

function toLeaf(menu: MeMenu, breadcrumb: string[]): NavLeaf {
  return {
    kind: 'leaf',
    key: menu.id,
    label: menu.name,
    breadcrumb,
    component: menu.component,
    path: menu.path,
    icon: menu.icon,
  }
}
