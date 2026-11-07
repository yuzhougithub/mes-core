import {
  BellOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  QuestionCircleOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Alert, Avatar, Button, Dropdown, Menu, Popover, Select, Tabs } from 'antd'
import { useState } from 'react'
import { formatProblem } from '../api/problem.ts'
import { MaterialListPage } from '../materials/MaterialListPage.tsx'
import { usePlant } from '../plant/PlantProvider.tsx'
import { Placeholder } from './Placeholder.tsx'
import { MODULES, findModule, type ModuleKey } from './nav.ts'

function ToolButton({
  label,
  icon,
  title,
  content,
}: {
  label: string
  icon: React.ReactNode
  title: string
  content: string
}) {
  return (
    <Popover title={title} content={<div style={{ maxWidth: 280 }}>{content}</div>}>
      <Button type="text" aria-label={label} icon={icon} />
    </Popover>
  )
}

function PlantSwitcher({ collapsed }: { collapsed: boolean }) {
  const { plants, plantsTruncated, loading, error, plantId, selectPlant, reloadPlants } = usePlant()
  if (error && plants.length === 0) {
    return collapsed ? null : (
      <div className="mes-plant">
        <Alert
          type="error"
          showIcon
          message="工厂加载失败"
          description={<Button size="small" onClick={reloadPlants}>重试</Button>}
        />
      </div>
    )
  }
  const options = plants.map((plant) => ({
    value: plant.id,
    label: `${plant.name}（${plant.code}）`,
  }))
  if (collapsed) {
    return (
      <div className="mes-plant mes-plant-collapsed">
        <Dropdown
          menu={{
            items: options.map((option) => ({ key: option.value, label: option.label })),
            onClick: ({ key }) => selectPlant(String(key)),
          }}
        >
          <Button type="text" aria-label="当前工厂" loading={loading}>厂</Button>
        </Dropdown>
      </div>
    )
  }
  return (
    <div className="mes-plant">
      <div className="mes-plant-label">当前工厂</div>
      <Select
        aria-label="当前工厂"
        style={{ width: '100%' }}
        placeholder={loading ? '正在加载工厂' : '选择工厂'}
        loading={loading}
        virtual={false}
        showSearch
        optionFilterProp="label"
        value={plantId ?? undefined}
        options={options}
        onChange={selectPlant}
        notFoundContent={loading ? '正在加载' : '没有可用工厂'}
      />
      {plantsTruncated ? (
        <div className="mes-plant-note">工厂超过 200 家，下拉不是全量。</div>
      ) : null}
      {error ? (
        <div className="mes-plant-note">{formatProblem(error.problem)}</div>
      ) : null}
    </div>
  )
}

export function AdminShell() {
  const [moduleKey, setModuleKey] = useState<ModuleKey>('material')
  const [itemKey, setItemKey] = useState('materials')
  const [collapsed, setCollapsed] = useState(false)
  const module = findModule(moduleKey)
  const item = module.items.find((entry) => entry.key === itemKey) ?? module.items[0]

  return (
    <div className="mes-shell">
      <header className="mes-topbar">
        <div className="mes-brand">
          <img className="mes-logo" src="/favicon.svg" alt="" width={32} height={32} />
          <span className="mes-name">NordMES</span>
        </div>
        <Tabs
          className="mes-module-tabs"
          centered
          activeKey={moduleKey}
          onChange={(key) => {
            const next = findModule(key as ModuleKey)
            setModuleKey(next.key)
            setItemKey(next.items[0].key)
          }}
          items={MODULES.map((entry) => ({ key: entry.key, label: entry.label }))}
        />
        <div className="mes-tools">
          <ToolButton
            label="搜索"
            icon={<SearchOutlined />}
            title="搜索"
            content="全局搜索没有接口。请在物料主数据中按编码或名称查询。"
          />
          <ToolButton
            label="通知"
            icon={<BellOutlined />}
            title="通知"
            content="通知接口未实现，这里没有未读消息。"
          />
          <ToolButton
            label="帮助"
            icon={<QuestionCircleOutlined />}
            title="帮助"
            content="当前只接通物料主数据。其他菜单是占位，没有模拟业务数据。"
          />
          <Dropdown
            menu={{
              items: [{
                key: 'auth',
                disabled: true,
                label: '登录接口未实现（无 /api/v1/auth/me）',
              }],
            }}
          >
            <Button type="text" aria-label="当前用户" icon={<Avatar size="small" icon={<UserOutlined />} />} />
          </Dropdown>
        </div>
      </header>
      <div className="mes-body">
        <aside className={collapsed ? 'mes-sider is-collapsed' : 'mes-sider'}>
          <PlantSwitcher collapsed={collapsed} />
          <Menu
            mode="inline"
            inlineCollapsed={collapsed}
            selectedKeys={[item.key]}
            items={module.items.map((entry) => ({ key: entry.key, label: entry.label }))}
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
          <h1 className="mes-title">{item.title}</h1>
          <p className="mes-subtitle">{item.subtitle}</p>
          {item.key === 'materials' ? <MaterialListPage /> : <Placeholder item={item} />}
        </main>
      </div>
    </div>
  )
}
