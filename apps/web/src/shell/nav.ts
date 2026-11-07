export type ModuleKey =
  | 'workbench'
  | 'production'
  | 'quality'
  | 'equipment'
  | 'material'
  | 'routing'
  | 'system'

export type NavItem = {
  key: string
  label: string
  title: string
  subtitle: string
  breadcrumb: string[]
  /** Shown when the page is not implemented. */
  placeholder?: string
}

export type NavModule = {
  key: ModuleKey
  label: string
  items: NavItem[]
}

export const MODULES: NavModule[] = [
  {
    key: 'workbench',
    label: '工作台',
    items: [
      {
        key: 'overview',
        label: '概览',
        title: '工作台',
        subtitle: '即将上线。',
        breadcrumb: ['工作台', '概览'],
        placeholder: '即将上线。',
      },
    ],
  },
  {
    key: 'production',
    label: '生产管理',
    items: [
      {
        key: 'orders',
        label: '生产订单',
        title: '生产订单',
        subtitle: '即将上线。',
        breadcrumb: ['生产管理', '生产订单'],
        placeholder: '即将上线。',
      },
      {
        key: 'jobs',
        label: '工单',
        title: '工单',
        subtitle: '即将上线。',
        breadcrumb: ['生产管理', '工单'],
        placeholder: '即将上线。',
      },
    ],
  },
  {
    key: 'quality',
    label: '质量管理',
    items: [
      {
        key: 'inspections',
        label: '检验',
        title: '检验',
        subtitle: '即将上线。',
        breadcrumb: ['质量管理', '检验'],
        placeholder: '即将上线。',
      },
    ],
  },
  {
    key: 'equipment',
    label: '设备管理',
    items: [
      {
        key: 'ledger',
        label: '设备台账',
        title: '设备台账',
        subtitle: '即将上线。',
        breadcrumb: ['设备管理', '设备台账'],
        placeholder: '即将上线。',
      },
    ],
  },
  {
    key: 'material',
    label: '物料管理',
    items: [
      {
        key: 'materials',
        label: '物料主数据',
        title: '物料主数据',
        subtitle: '维护当前工厂可见的物料。',
        breadcrumb: ['物料管理', '物料主数据'],
      },
      {
        key: 'categories',
        label: '物料分类',
        title: '物料分类',
        subtitle: '即将上线。',
        breadcrumb: ['物料管理', '物料分类'],
        placeholder: '即将上线。',
      },
      {
        key: 'uoms',
        label: '计量单位',
        title: '计量单位',
        subtitle: '即将上线。',
        breadcrumb: ['物料管理', '计量单位'],
        placeholder: '即将上线。',
      },
      {
        key: 'boms',
        label: 'BOM',
        title: 'BOM',
        subtitle: '即将上线。',
        breadcrumb: ['物料管理', 'BOM'],
        placeholder: '即将上线。',
      },
    ],
  },
  {
    key: 'routing',
    label: '工艺管理',
    items: [
      {
        key: 'operations',
        label: '工序',
        title: '工序',
        subtitle: '即将上线。',
        breadcrumb: ['工艺管理', '工序'],
        placeholder: '即将上线。',
      },
      {
        key: 'routings',
        label: '工艺路线',
        title: '工艺路线',
        subtitle: '即将上线。',
        breadcrumb: ['工艺管理', '工艺路线'],
        placeholder: '即将上线。',
      },
    ],
  },
  {
    key: 'system',
    label: '系统管理',
    items: [
      {
        key: 'plants',
        label: '工厂',
        title: '工厂',
        subtitle: '即将上线。',
        breadcrumb: ['系统管理', '工厂'],
        placeholder: '即将上线。',
      },
      {
        key: 'schemas',
        label: '自定义字段',
        title: '自定义字段',
        subtitle: '即将上线。',
        breadcrumb: ['系统管理', '自定义字段'],
        placeholder: '即将上线。',
      },
    ],
  },
]

export function findModule(key: ModuleKey): NavModule {
  return MODULES.find((item) => item.key === key) ?? MODULES[0]
}
