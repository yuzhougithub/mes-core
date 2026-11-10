import type { MenuNode, MenuWrite } from '../api/menus.ts'

export const MENU_TYPES = [
  { value: 'DIR', label: '目录' },
  { value: 'MENU', label: '菜单' },
  { value: 'BUTTON', label: '按钮' },
] as const

export const ROOT_PARENT = 'root'

const PERM = /^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*){1,3}$/

export function menuTypeLabel(type: string): string {
  return MENU_TYPES.find((item) => item.value === type)?.label ?? type
}

export function filterMenuTree(nodes: MenuNode[], keyword: string): MenuNode[] {
  const query = keyword.trim().toLowerCase()
  if (!query) return nodes
  const next: MenuNode[] = []
  for (const node of nodes) {
    const children = filterMenuTree(node.children, keyword)
    const self = [node.name, node.permCode, node.path, node.component]
      .some((value) => value?.toLowerCase().includes(query))
    if (self) next.push(node)
    else if (children.length > 0) next.push({ ...node, children })
  }
  return next
}

export function findMenu(nodes: readonly MenuNode[], id: string): MenuNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    const child = findMenu(node.children, id)
    if (child) return child
  }
  return null
}

export function expandableIds(nodes: readonly MenuNode[]): string[] {
  const ids: string[] = []
  const walk = (rows: readonly MenuNode[]) => {
    for (const row of rows) {
      if (row.children.length === 0) continue
      ids.push(row.id)
      walk(row.children)
    }
  }
  walk(nodes)
  return ids
}

export type ParentChoice = {
  value: string
  title: string
  selectable: boolean
  disabled: boolean
  children?: ParentChoice[]
}

export function parentChoices(nodes: readonly MenuNode[], type: string, selfId: string | null): ParentChoice[] {
  const self = selfId ? findMenu(nodes, selfId) : null
  const blocked = new Set<string>(self ? [self.id, ...descendantIds(self)] : [])
  if (type === 'BUTTON') return menuChoices(nodes, blocked)
  return [
    { value: ROOT_PARENT, title: '根', selectable: true, disabled: false },
    ...dirChoices(nodes, blocked),
  ]
}

export function cleanMenuWrite(input: MenuWrite): MenuWrite {
  const perm = text(input.permCode)
  const path = text(input.path)
  const component = text(input.component)
  return {
    ...input,
    name: input.name.trim(),
    permCode: input.type === 'DIR' ? null : perm,
    path: input.type === 'MENU' ? path : null,
    component: input.type === 'MENU' ? component : null,
    icon: text(input.icon),
    sortNo: Number.isFinite(input.sortNo) ? input.sortNo : 0,
  }
}

export function toWrite(node: MenuNode, patch: Partial<MenuWrite> = {}): MenuWrite {
  return cleanMenuWrite({
    parentId: patch.parentId !== undefined ? patch.parentId : node.parentId,
    type: patch.type ?? node.type,
    name: patch.name ?? node.name,
    permCode: patch.permCode !== undefined ? patch.permCode : node.permCode,
    path: patch.path !== undefined ? patch.path : node.path,
    component: patch.component !== undefined ? patch.component : node.component,
    icon: patch.icon !== undefined ? patch.icon : node.icon,
    sortNo: patch.sortNo ?? node.sortNo,
    visible: patch.visible ?? node.visible,
    enabled: patch.enabled ?? node.enabled,
    lockVersion: node.lockVersion,
  })
}

export function menuProblems(input: MenuWrite): string[] {
  const problems: string[] = []
  if (input.type !== 'DIR' && input.type !== 'MENU' && input.type !== 'BUTTON') {
    problems.push('类型只能是目录、菜单或按钮')
  }
  const name = input.name.trim()
  if (!name) problems.push('请输入名称')
  else if ([...name].length > 64) problems.push('名称最长 64 字')
  const perm = text(input.permCode)
  const path = text(input.path)
  const component = text(input.component)
  const icon = text(input.icon)
  if (perm && !PERM.test(perm)) problems.push('权限点编码格式不正确')
  if (input.type === 'DIR') {
    if (perm) problems.push('目录不能填写权限点')
    if (path || component) problems.push('目录不能填写路由')
  }
  if (input.type === 'MENU') {
    if (!path) problems.push('菜单必须填写 path')
    if (!component) problems.push('菜单必须填写 component')
  }
  if (input.type === 'BUTTON') {
    if (!perm) problems.push('按钮必须填写权限点')
    if (path || component) problems.push('按钮不能填写路由')
    if (!input.parentId) problems.push('按钮的父节点必须是菜单')
  }
  if (icon && [...icon].length > 64) problems.push('图标最长 64 字')
  if (path && [...path].length > 128) problems.push('path 最长 128 字')
  if (component && [...component].length > 128) problems.push('component 最长 128 字')
  return problems
}

export function parentProblems(
  nodes: readonly MenuNode[],
  parentId: string | null,
  type: string,
  selfId: string | null,
): string[] {
  if (selfId && parentId === selfId) return ['不能把节点移到自身下面']
  if (selfId && parentId && isDescendant(nodes, selfId, parentId)) return ['不能把节点移到自己的子孙下面']
  if (!parentId) {
    if (type === 'BUTTON') return ['按钮的父节点必须是菜单']
    return []
  }
  const parent = findMenu(nodes, parentId)
  if (!parent) return ['父节点不存在']
  if (type === 'BUTTON' && parent.type !== 'MENU') return ['按钮的父节点必须是菜单']
  if ((type === 'DIR' || type === 'MENU') && parent.type !== 'DIR') return ['父节点只能是目录']
  return []
}

export function suggestChildType(parentType: string): 'DIR' | 'MENU' | 'BUTTON' {
  if (parentType === 'MENU') return 'BUTTON'
  return 'MENU'
}

function text(value: string | null | undefined): string | null {
  if (value == null) return null
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function descendantIds(node: MenuNode): string[] {
  const ids: string[] = []
  const walk = (rows: readonly MenuNode[]) => {
    for (const row of rows) {
      ids.push(row.id)
      walk(row.children)
    }
  }
  walk(node.children)
  return ids
}

function isDescendant(nodes: readonly MenuNode[], ancestorId: string, nodeId: string): boolean {
  const ancestor = findMenu(nodes, ancestorId)
  if (!ancestor) return false
  return descendantIds(ancestor).includes(nodeId)
}

function dirChoices(nodes: readonly MenuNode[], blocked: Set<string>): ParentChoice[] {
  const result: ParentChoice[] = []
  for (const node of nodes) {
    if (node.type !== 'DIR') continue
    const children = dirChoices(node.children, blocked)
    const disabled = blocked.has(node.id)
    result.push({
      value: node.id,
      title: node.name,
      selectable: !disabled,
      disabled,
      children: children.length > 0 ? children : undefined,
    })
  }
  return result
}

function menuChoices(nodes: readonly MenuNode[], blocked: Set<string>): ParentChoice[] {
  const result: ParentChoice[] = []
  for (const node of nodes) {
    if (node.type === 'MENU') {
      const disabled = blocked.has(node.id)
      result.push({
        value: node.id,
        title: node.name,
        selectable: !disabled,
        disabled,
      })
      continue
    }
    if (node.type !== 'DIR') continue
    const children = menuChoices(node.children, blocked)
    if (children.length === 0) continue
    result.push({
      value: node.id,
      title: node.name,
      selectable: false,
      disabled: true,
      children,
    })
  }
  return result
}
