import {
  ApartmentOutlined,
  AppstoreOutlined,
  BankOutlined,
  BellOutlined,
  BranchesOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  FormOutlined,
  GoldOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DownOutlined,
  ProfileOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  ShareAltOutlined,
  TagOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Alert, Avatar, Button, Dropdown, Menu, Popover, Select, Tabs } from 'antd'
import { useState, type ReactNode } from 'react'
import { useBrand } from '../brand/BrandProvider.tsx'
import { MaterialListPage } from '../materials/MaterialListPage.tsx'
import { usePlant } from '../plant/PlantProvider.tsx'
import { Placeholder } from './Placeholder.tsx'
import { MODULES, findModule, type ModuleKey } from './nav.ts'

const MENU_ICONS: Record<string, ReactNode> = {
  overview: <AppstoreOutlined />,
  orders: <ProfileOutlined />,
  jobs: <FileTextOutlined />,
  inspections: <SafetyCertificateOutlined />,
  ledger: <ToolOutlined />,
  materials: <GoldOutlined />,
  categories: <ApartmentOutlined />,
  uoms: <TagOutlined />,
  boms: <DatabaseOutlined />,
  operations: <ShareAltOutlined />,
  routings: <BranchesOutlined />,
  plants: <BankOutlined />,
  schemas: <FormOutlined />,
}

function ToolButton({
  label,
  icon,
}: {
  label: string
  icon: ReactNode
}) {
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

export function AdminShell() {
  const brand = useBrand()
  const [moduleKey, setModuleKey] = useState<ModuleKey>('material')
  const [itemKey, setItemKey] = useState('materials')
  const [collapsed, setCollapsed] = useState(false)
  const module = findModule(moduleKey)
  const item = module.items.find((entry) => entry.key === itemKey) ?? module.items[0]

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
        <Tabs
          className="mes-module-tabs"
          activeKey={moduleKey}
          onChange={(key) => {
            const next = findModule(key as ModuleKey)
            setModuleKey(next.key)
            setItemKey(next.items[0].key)
          }}
          items={MODULES.map((entry) => ({ key: entry.key, label: entry.label }))}
        />
        <PlantSelect />
        <div className="mes-tools">
          <ToolButton label="搜索" icon={<SearchOutlined />} />
          <ToolButton label="通知" icon={<BellOutlined />} />
          <ToolButton label="帮助" icon={<QuestionCircleOutlined />} />
          <Dropdown
            menu={{
              items: [{ key: 'auth', disabled: true, label: '即将上线' }],
            }}
          >
            <Button type="text" aria-label="当前用户" icon={<Avatar size="small" icon={<UserOutlined />} />} />
          </Dropdown>
        </div>
      </header>
      <div className="mes-body">
        <aside className={collapsed ? 'mes-sider is-collapsed' : 'mes-sider'}>
          {collapsed ? null : (
            <div className="mes-menu-group">
              <span className="mes-menu-group-icon">{MENU_ICONS[module.items[0]?.key ?? '']}</span>
              <span>{module.label}</span>
              <DownOutlined />
            </div>
          )}
          <Menu
            mode="inline"
            inlineCollapsed={collapsed}
            selectedKeys={[item.key]}
            items={module.items.map((entry) => ({
              key: entry.key,
              icon: MENU_ICONS[entry.key],
              label: entry.label,
            }))}
            onClick={({ key }) => setItemKey(String(key))}
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
            {item.breadcrumb.join(' / ')}
          </div>
          {item.key === 'materials' ? <MaterialListPage /> : <Placeholder item={item} />}
        </main>
      </div>
    </div>
  )
}
