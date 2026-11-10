import {
  AppstoreOutlined,
  BankOutlined,
  BellOutlined,
  BranchesOutlined,
  FileTextOutlined,
  FormOutlined,
  GoldOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DownOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  ShareAltOutlined,
  TagOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Alert, Button, Menu, Popover, Select, Tabs } from 'antd'
import type { MenuProps } from 'antd'
import { useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/useAuth.ts'
import { useBrand } from '../brand/useBrand.ts'
import { usePlant } from '../plant/usePlant.ts'
import { PageOutlet } from './pages.tsx'
import { Placeholder } from './Placeholder.tsx'
import { UserAccount } from './UserAccount.tsx'
import { dirKeys, findLeaf, firstLeaf, navFromMenus, type NavNode } from './nav-model.ts'

function menuIcon(icon: string | null, component: string | null, kind: 'dir' | 'leaf'): ReactNode {
  const mapped = (icon && ICONS[icon]) || (component && ICONS[component])
  return <span className="mes-menu-icon" aria-hidden="true">{mapped ?? (kind === 'dir' ? <AppstoreOutlined /> : <FileTextOutlined />)}</span>
}

const ICONS: Record<string, ReactNode> = {
  'md/material': <GoldOutlined />,
  'md/uom': <TagOutlined />,
  'rtg/operation': <ShareAltOutlined />,
  'rtg/routing': <BranchesOutlined />,
  'org/plant': <BankOutlined />,
  'sys/user': <TeamOutlined />,
  'sys/role': <SafetyCertificateOutlined />,
  'sys/menu': <AppstoreOutlined />,
  'cfg/field-schema': <FormOutlined />,
}

function ToolButton({ label, icon }: { label: string; icon: ReactNode }) {
  return (
    <Popover content="即将上线">
      <Button type="text" aria-label={label} icon={icon} />
    </Popover>
  )
}

function PlantSelect() {
  const { plants, loading, error, plantId, selectPlant, reloadPlants } = usePlant()
  const options = plants.map((plant) => ({
    value: plant.id,
    label: `${plant.name}（${plant.code}）`,
  }))
  if (error && plants.length === 0) {
    return (
      <div className="mes-plant-select">
        <Alert
          type="error"
          showIcon
          message="工厂加载失败"
          action={<Button size="small" onClick={reloadPlants}>重试</Button>}
        />
      </div>
    )
  }
  return (
    <div className="mes-plant-select">
      <BankOutlined className="mes-plant-icon" />
      <Select
        aria-label="当前工厂"
        variant="borderless"
        placeholder={loading ? '加载中' : '选择工厂'}
        loading={loading}
        virtual={false}
        showSearch
        optionFilterProp="label"
        popupMatchSelectWidth={false}
        value={plantId ?? undefined}
        options={options}
        onChange={selectPlant}
        notFoundContent={loading ? '正在加载' : '没有可用工厂'}
      />
    </div>
  )
}

function menuItems(nodes: readonly NavNode[]): MenuProps['items'] {
  return nodes.map((node) => {
    if (node.kind === 'dir') {
      return {
        key: node.key,
        icon: menuIcon(node.icon, null, 'dir'),
        label: node.label,
        children: menuItems(node.children),
      }
    }
    return {
      key: node.key,
      icon: menuIcon(node.icon, node.component, 'leaf'),
      label: node.label,
    }
  })
}

export function AdminShell() {
  const brand = useBrand()
  const auth = useAuth()
  const { plantId } = usePlant()
  const modules = useMemo(() => navFromMenus(auth.me?.menus ?? []), [auth.me])
  const [moduleKey, setModuleKey] = useState<string | null>(null)
  const [leafKey, setLeafKey] = useState<string | null>(null)
  const [collapsed, setCollapsed] = useState(false)
  const module = modules.find((item) => item.key === moduleKey) ?? modules[0] ?? null
  const leaf = module ? (findLeaf(module.nodes, leafKey) ?? firstLeaf(module.nodes)) : null
  const moduleId = module?.key ?? null
  const openDefaults = useMemo(() => dirKeys(module?.nodes ?? []), [module])
  const [openState, setOpenState] = useState<{ moduleId: string | null; keys: string[] } | null>(null)
  const openKeys = openState?.moduleId === moduleId ? openState.keys : openDefaults

  return (
    <div className="mes-shell">
      <header className="mes-topbar">
        <div className="mes-brand">
          <img className="mes-logo" src={brand.logo} alt="" width={32} height={32} />
          <div className="mes-brand-text">
            <span className="mes-name" title={brand.name}>{brand.name}</span>
            {brand.subtitle ? <span className="mes-brand-sub" title={brand.subtitle}>{brand.subtitle}</span> : null}
          </div>
        </div>
        {modules.length > 0 ? (
          <Tabs
            className="mes-module-tabs"
            activeKey={module?.key}
            onChange={(key) => {
              const next = modules.find((item) => item.key === key)
              setModuleKey(key)
              setLeafKey(next ? firstLeaf(next.nodes)?.key ?? null : null)
            }}
            items={modules.map((entry) => ({ key: entry.key, label: entry.label }))}
          />
        ) : <div className="mes-module-tabs" />}
        <PlantSelect />
        <div className="mes-tools">
          <ToolButton label="搜索" icon={<SearchOutlined />} />
          <ToolButton label="通知" icon={<BellOutlined />} />
          <ToolButton label="帮助" icon={<QuestionCircleOutlined />} />
          <UserAccount />
        </div>
      </header>
      <div className="mes-body">
        <aside className={collapsed ? 'mes-sider is-collapsed' : 'mes-sider'}>
          {collapsed || !module ? null : (
            <div className="mes-menu-group">
              <span className="mes-menu-group-icon">{menuIcon(module.icon, null, 'dir')}</span>
              <span>{module.label}</span>
              <DownOutlined />
            </div>
          )}
          <Menu
            mode="inline"
            inlineCollapsed={collapsed}
            selectedKeys={leaf ? [leaf.key] : []}
            {...(collapsed ? {} : {
              openKeys,
              onOpenChange: (keys) => setOpenState({ moduleId, keys: keys.map(String) }),
            })}
            items={module ? menuItems(module.nodes) : []}
            onClick={({ key }) => setLeafKey(String(key))}
          />
          <div className="mes-sider-foot">
            <Button
              type="text"
              aria-label={collapsed ? '展开菜单' : '收起菜单'}
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((value) => !value)}
            />
          </div>
        </aside>
        <main className="mes-content">
          <div className="mes-breadcrumb">
            {leaf ? leaf.breadcrumb.join(' / ') : ''}
          </div>
          {leaf ? (
            <PageOutlet key={`${plantId ?? ''}:${leaf.key}`} component={leaf.component} />
          ) : (
            <Placeholder message="没有可访问的菜单" />
          )}
        </main>
      </div>
    </div>
  )
}
