import { BankOutlined, TeamOutlined } from '@ant-design/icons'
import { App, Button, Card, Form, Input, Menu, Switch } from 'antd'
import { useState } from 'react'
import { createPlant } from '../api/resources.ts'
import { formatProblem, isAuthRedirect, isStale, toApiError } from '../api/problem.ts'
import { useBrand } from '../brand/useBrand.ts'
import { getAuthSnapshot, recoverPlantAccess } from '../auth/store.ts'
import { UserAccount } from '../shell/UserAccount.tsx'
import { UserListPage } from '../users/UserListPage.tsx'
import type { PlantChoice } from '../users/UserEditor.tsx'

type Section = 'plant' | 'users'

type PlantForm = {
  code: string
  name: string
  timezone: string
  enabled: boolean
  remark: string
}

export function BootstrapPage() {
  const brand = useBrand()
  const { message } = App.useApp()
  const [section, setSection] = useState<Section>('plant')
  const [created, setCreated] = useState<PlantChoice[]>([])
  const [saving, setSaving] = useState(false)
  const [checking, setChecking] = useState(false)
  const [form] = Form.useForm<PlantForm>()

  async function onCreate(values: PlantForm) {
    setSaving(true)
    try {
      const plant = await createPlant({
        code: values.code.trim(),
        name: values.name.trim(),
        timezone: values.timezone.trim() || 'Asia/Shanghai',
        enabled: values.enabled,
        remark: values.remark.trim() || null,
      })
      setCreated((prev) => {
        const next = prev.filter((item) => item.id !== plant.id)
        next.push({ id: plant.id, code: plant.code, name: plant.name })
        return next
      })
      form.resetFields()
      message.success('已创建工厂')
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setSaving(false)
    }
  }

  async function enter() {
    setChecking(true)
    try {
      await recoverPlantAccess()
      if (getAuthSnapshot().phase === 'no-plant') message.info('还没有可用工厂')
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setChecking(false)
    }
  }

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
        <div className="mes-tools">
          <UserAccount />
        </div>
      </header>
      <div className="mes-body">
        <aside className="mes-sider">
          <Menu
            mode="inline"
            selectedKeys={[section]}
            items={[
              { key: 'plant', icon: <BankOutlined />, label: '新建工厂' },
              { key: 'users', icon: <TeamOutlined />, label: '用户管理' },
            ]}
            onClick={({ key }) => setSection(key === 'users' ? 'users' : 'plant')}
          />
        </aside>
        <main className="mes-content">
          <div className="mes-breadcrumb">
            {section === 'plant' ? '系统管理 / 新建工厂' : '系统管理 / 用户'}
          </div>
          {section === 'plant' ? (
            <>
              <p className="mes-auth-lead">当前账号还没有工厂任职。新建工厂后，到用户管理里把账号任职到该工厂。</p>
              <Card>
                <Form
                  form={form}
                  layout="vertical"
                  requiredMark={false}
                  initialValues={{ timezone: 'Asia/Shanghai', enabled: true, code: '', name: '', remark: '' }}
                  onFinish={(values) => { void onCreate(values) }}
                >
                  <Form.Item label="工厂编码" name="code" rules={[{ required: true, message: '请输入工厂编码' }]}>
                    <Input autoComplete="off" />
                  </Form.Item>
                  <Form.Item label="工厂名称" name="name" rules={[{ required: true, message: '请输入工厂名称' }]}>
                    <Input autoComplete="off" />
                  </Form.Item>
                  <Form.Item label="时区" name="timezone" rules={[{ required: true, message: '请输入时区' }]}>
                    <Input autoComplete="off" />
                  </Form.Item>
                  <Form.Item label="启用" name="enabled" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item label="备注" name="remark">
                    <Input.TextArea rows={2} />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" loading={saving}>创建工厂</Button>
                </Form>
              </Card>
              {created.length > 0 ? (
                <div className="mes-created-plants">
                  {created.map((plant) => (
                    <div key={plant.id}>{plant.name}（{plant.code}）</div>
                  ))}
                </div>
              ) : null}
              <Button style={{ marginTop: 16 }} loading={checking} onClick={() => { void enter() }}>进入系统</Button>
            </>
          ) : (
            <UserListPage extraPlants={created} recoverWhenSelfAssigned />
          )}
        </main>
      </div>
    </div>
  )
}
