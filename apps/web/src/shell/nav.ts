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
        subtitle: '工作台尚未实现。本迭代不做首页大屏。',
        breadcrumb: ['工作台', '概览'],
        placeholder: '这里没有统计卡、待办或图表。工作台接口与页面都未实现。',
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
        subtitle: '生产管理尚未实现。',
        breadcrumb: ['生产管理', '生产订单'],
        placeholder: '生产订单没有对应接口，此页只是导航占位。',
      },
      {
        key: 'jobs',
        label: '工单',
        title: '工单',
        subtitle: '生产管理尚未实现。',
        breadcrumb: ['生产管理', '工单'],
        placeholder: '工单没有对应接口，此页只是导航占位。',
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
        subtitle: '质量管理尚未实现。',
        breadcrumb: ['质量管理', '检验'],
        placeholder: '检验执行不在当前范围，没有可用页面。',
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
        subtitle: '设备管理尚未实现。',
        breadcrumb: ['设备管理', '设备台账'],
        placeholder: '设备台账接口尚未提供，此页只是导航占位。',
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
        subtitle: '维护当前工厂可见的共享物料与本厂物料。切换工厂会中止未完成请求并重新加载。',
        breadcrumb: ['物料管理', '物料主数据'],
      },
      {
        key: 'categories',
        label: '物料分类',
        title: '物料分类',
        subtitle: '物料分类尚未实现。',
        breadcrumb: ['物料管理', '物料分类'],
        placeholder: 'GET /api/v1/material-categories 未实现，不能维护或按名称选择分类。',
      },
      {
        key: 'uoms',
        label: '计量单位',
        title: '计量单位',
        subtitle: '计量单位页面尚未展开。',
        breadcrumb: ['物料管理', '计量单位'],
        placeholder: '计量单位接口已存在，但本迭代只做物料主数据列表，不展开单位维护页。',
      },
      {
        key: 'boms',
        label: 'BOM',
        title: 'BOM',
        subtitle: 'BOM 尚未实现。',
        breadcrumb: ['物料管理', 'BOM'],
        placeholder: 'BOM 页面未实现。',
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
        subtitle: '工序页面尚未展开。',
        breadcrumb: ['工艺管理', '工序'],
        placeholder: '本迭代不展开工序页面。',
      },
      {
        key: 'routings',
        label: '工艺路线',
        title: '工艺路线',
        subtitle: '工艺路线画布本迭代不实现。',
        breadcrumb: ['工艺管理', '工艺路线'],
        placeholder: '工艺路线画布后续使用 @xyflow/react。本迭代不实现，也不引入 @xrenders/xflow。',
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
        subtitle: '工厂维护页面尚未展开。',
        breadcrumb: ['系统管理', '工厂'],
        placeholder: '当前工厂用左侧下拉切换。工厂维护页面本迭代不展开。',
      },
      {
        key: 'schemas',
        label: '自定义字段',
        title: '自定义字段',
        subtitle: '自定义字段配置页面尚未展开。',
        breadcrumb: ['系统管理', '自定义字段'],
        placeholder: '物料表单会读取当前工厂的 material 自定义字段。配置页面本迭代不展开。',
      },
    ],
  },
]

export function findModule(key: ModuleKey): NavModule {
  return MODULES.find((item) => item.key === key) ?? MODULES[0]
}
