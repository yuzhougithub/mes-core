export const DATA_SCOPES = [
  { value: 'ALL', label: '全部' },
  { value: 'PLANT', label: '本工厂' },
  { value: 'DEPT_AND_CHILD', label: '本部门及以下' },
  { value: 'DEPT', label: '本部门' },
  { value: 'SELF', label: '仅本人' },
  { value: 'CUSTOM', label: '自定义部门' },
] as const

export function dataScopeLabel(value: string): string {
  return DATA_SCOPES.find((item) => item.value === value)?.label ?? value
}

const ROLE_CODE = /^[A-Z][A-Z0-9_]{0,31}$/

export function roleCodeProblems(code: string): string[] {
  if (!ROLE_CODE.test(code)) {
    return ['角色编码须为大写字母开头，仅含大写字母、数字或下划线']
  }
  return []
}
