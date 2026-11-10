export type GrantSource = {
  id: string
  type: string
  name?: string
  permCode?: string | null
  children?: readonly GrantSource[]
}

export type GrantTreeNode = {
  id: string
  name: string
  type: string
  permCode: string | null
  checkable: boolean
  children: GrantTreeNode[]
}

/** Menu and button nodes can be granted. Directories only structure the tree. */
export function isGrantable(type: string): boolean {
  return type === 'MENU' || type === 'BUTTON'
}

export function grantTree(nodes: readonly GrantSource[]): GrantTreeNode[] {
  return nodes.map((node) => ({
    id: node.id,
    name: node.name ?? '',
    type: node.type,
    permCode: node.permCode ?? null,
    checkable: isGrantable(node.type),
    children: grantTree(node.children ?? []),
  }))
}

/** Ids to store: checked menu and button nodes only, in tree order. */
export function sanitizeGrant(checked: readonly string[], nodes: readonly GrantSource[]): string[] {
  const wanted = new Set(checked)
  const ids: string[] = []
  const walk = (rows: readonly GrantSource[]) => {
    for (const row of rows) {
      if (wanted.has(row.id) && isGrantable(row.type)) ids.push(row.id)
      walk(row.children ?? [])
    }
  }
  walk(nodes)
  return ids
}
