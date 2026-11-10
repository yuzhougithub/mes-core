import { App, Button, Checkbox, Drawer, Form, Input, Radio, Select, Space, Switch, Tooltip } from 'antd'
import { useEffect, useState } from 'react'
import { formatProblem, isAuthRedirect, isStale, toApiError, type ApiError } from '../api/problem.ts'
import {
  createUser,
  getUser,
  replaceUserPlants,
  updateUser,
  type PlantBinding,
  type PlantBindingInput,
  type UserDetail,
} from '../api/users.ts'
import { maskEmail, maskMobile } from '../auth/mask.ts'
import { passwordProblems } from '../auth/password.ts'

export type EditorMode = 'create' | 'edit' | 'detail'

export type PlantChoice = {
  id: string
  code: string
  name: string
}

type BindingDraft = {
  plantId: string
  isDefault: boolean
  active: boolean
  jobTitle: string
}

type Props = {
  open: boolean
  mode: EditorMode
  userId?: string
  superAdmin: boolean
  plants: PlantChoice[]
  onClose: () => void
  onSaved: (userId: string, initialPassword?: string | null) => void
}

const EMPTY = {
  username: '',
  password: '',
  code: '',
  name: '',
  mobile: '',
  email: '',
  remark: '',
  superAdmin: false,
}

export function UserEditor(props: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<typeof EMPTY>()
  const [loading, setLoading] = useState(props.mode !== 'create')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [detail, setDetail] = useState<UserDetail | null>(null)
  const [bindings, setBindings] = useState<BindingDraft[]>(() => initialBindings(props.mode, props.plants))
  const title = props.mode === 'create' ? '新增用户' : props.mode === 'edit' ? '编辑用户' : '用户详情'

  useEffect(() => {
    if (props.mode === 'create' || !props.userId) return
    let cancel = false
    getUser(props.userId)
      .then((row) => {
        if (cancel) return
        setDetail(row)
        form.setFieldsValue({
          username: row.username,
          password: '',
          code: row.person.code,
          name: row.person.name,
          mobile: row.person.mobile ?? '',
          email: row.person.email ?? '',
          remark: row.person.remark ?? '',
          superAdmin: row.superAdmin,
        })
        const visible = new Set(props.plants.map((plant) => plant.id))
        const rows = row.plants
          .filter((plant) => props.superAdmin || visible.has(plant.plantId))
          .map(toDraft)
        setBindings(rows)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (cancel || isStale(err) || isAuthRedirect(err)) return
        setError(toApiError(err))
        setLoading(false)
      })
    return () => {
      cancel = true
    }
  }, [props.mode, props.userId, props.plants, props.superAdmin, form])

  function reload() {
    if (!props.userId) return
    setLoading(true)
    setError(null)
    getUser(props.userId)
      .then((row) => {
        setDetail(row)
        form.setFieldsValue({
          username: row.username,
          password: '',
          code: row.person.code,
          name: row.person.name,
          mobile: row.person.mobile ?? '',
          email: row.person.email ?? '',
          remark: row.person.remark ?? '',
          superAdmin: row.superAdmin,
        })
        const visible = new Set(props.plants.map((plant) => plant.id))
        setBindings(row.plants.filter((plant) => props.superAdmin || visible.has(plant.plantId)).map(toDraft))
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (isStale(err) || isAuthRedirect(err)) return
        setError(toApiError(err))
        setLoading(false)
      })
  }

  async function save() {
    const values = await form.validateFields()
    const prepared = normalizeBindings(bindings)
    if (prepared.length > 0 && prepared.filter((row) => row.isDefault).length !== 1) {
      message.error('须且仅能指定一个默认工厂')
      return
    }
    if (props.superAdmin && props.mode === 'create' && prepared.length === 0) {
      message.error('须且仅能指定一个默认工厂')
      return
    }
    if (prepared.some((row) => row.isDefault && !row.active)) {
      message.error('默认工厂必须是有效任职')
      return
    }
    const password = (values.password ?? '').trim()
    if (props.mode === 'create' && password) {
      const account = (values.username.trim() || values.code.trim()).toLowerCase()
      const problems = passwordProblems(password, account)
      if (problems.length) {
        form.setFields([{ name: 'password', errors: problems }])
        return
      }
    }
    setSaving(true)
    try {
      if (props.mode === 'create') {
        const created = await createUser({
          username: values.username.trim() || undefined,
          password: password || undefined,
          code: values.code.trim(),
          name: values.name.trim(),
          mobile: blank(values.mobile),
          email: blank(values.email),
          remark: blank(values.remark),
          superAdmin: props.superAdmin ? values.superAdmin : undefined,
          plants: prepared,
        })
        message.success('已创建')
        props.onSaved(created.id, created.initialPassword)
        return
      }
      if (!detail) return
      const updated = await updateUser(detail.id, {
        username: values.username.trim(),
        name: values.name.trim(),
        mobile: blank(values.mobile),
        email: blank(values.email),
        remark: blank(values.remark),
        lockVersion: detail.lockVersion,
      })
      const replaced = await replaceUserPlants(updated.id, updated.lockVersion, prepared)
      message.success('已保存')
      props.onSaved(replaced.id, null)
    } catch (err) {
      if (isStale(err) || isAuthRedirect(err)) return
      const api = toApiError(err)
      message.error(formatProblem(api.problem))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Drawer
      open={props.open}
      width={720}
      title={title}
      destroyOnHidden
      onClose={props.onClose}
      extra={props.mode === 'detail' ? null : (
        <Space>
          <Button onClick={props.onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={() => { void save() }}>保存</Button>
        </Space>
      )}
    >
      {error ? (
        <div className="mes-form-error">
          <div>{formatProblem(error.problem)}</div>
          <Button size="small" style={{ marginTop: 8 }} onClick={() => { void reload() }}>重试</Button>
        </div>
      ) : null}
      {loading ? <div>正在加载用户</div> : null}
      {!loading && !error && props.mode === 'detail' && detail ? <DetailView detail={detail} /> : null}
      {!loading && !error && props.mode !== 'detail' ? (
        <Form form={form} layout="vertical" requiredMark={false} initialValues={EMPTY}>
          <Form.Item label="用户名" name="username" extra={props.mode === 'create' ? '留空时使用工号的小写形式。' : undefined}>
            <Input placeholder="3–64 位小写字母、数字、.、_ 或 -" autoComplete="off" />
          </Form.Item>
          {props.mode === 'create' ? (
            <Form.Item label="工号" name="code" rules={[{ required: true, message: '请输入工号' }]}>
              <Input autoComplete="off" />
            </Form.Item>
          ) : (
            <Form.Item label="工号" name="code">
              <Input disabled />
            </Form.Item>
          )}
          <Form.Item label="姓名" name="name" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item label="手机号" name="mobile">
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item label="邮箱" name="email">
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} />
          </Form.Item>
          {props.mode === 'create' ? (
            <Form.Item label="初始口令" name="password" extra="留空则由系统生成，只显示一次。">
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          ) : null}
          {props.mode === 'create' && props.superAdmin ? (
            <Form.Item label="超级管理员" name="superAdmin" valuePropName="checked">
              <Switch />
            </Form.Item>
          ) : null}
          <div className="mes-group-title">工厂任职</div>
          <BindingEditor options={props.plants} value={bindings} onChange={setBindings} />
        </Form>
      ) : null}
    </Drawer>
  )
}

function DetailView({ detail }: { detail: UserDetail }) {
  return (
    <div className="mes-detail">
      <div>用户名：{detail.username}</div>
      <div>工号：{detail.person.code}</div>
      <div>姓名：{detail.person.name}</div>
      <div>手机号：{maskMobile(detail.person.mobile)}</div>
      <div>邮箱：{maskEmail(detail.person.email)}</div>
      <div>状态：{detail.status === 'DISABLED' ? '停用' : '启用'}</div>
      <div>备注：{detail.person.remark || '—'}</div>
      <div className="mes-group-title">工厂任职</div>
      {detail.plants.length === 0 ? <div>尚未任职</div> : detail.plants.map((plant) => (
        <div key={plant.plantId}>
          {plant.plantName || plant.plantId}
          {plant.plantCode ? `（${plant.plantCode}）` : ''}
          {plant.isDefault ? '，默认工厂' : ''}
          {plant.active ? '' : '，已停用任职'}
          {plant.jobTitle ? `，${plant.jobTitle}` : ''}
        </div>
      ))}
    </div>
  )
}

function BindingEditor({
  options,
  value,
  onChange,
}: {
  options: PlantChoice[]
  value: BindingDraft[]
  onChange: (next: BindingDraft[]) => void
}) {
  if (options.length === 0) {
    return <div>还没有可选工厂。</div>
  }
  const defaultId = value.find((row) => row.isDefault)?.plantId
  function update(next: BindingDraft[]) {
    if (next.length === 0) {
      onChange(next)
      return
    }
    if (!next.some((row) => row.isDefault && row.active)) {
      const candidate = next.find((row) => row.active) ?? next[0]
      onChange(next.map((row) => ({ ...row, isDefault: row.plantId === candidate.plantId })))
      return
    }
    onChange(next)
  }
  return (
    <div>
      {options.map((option) => {
        const row = value.find((item) => item.plantId === option.id)
        return (
          <div className="mes-binding-row" key={option.id}>
            <Checkbox
              checked={Boolean(row)}
              onChange={(event) => {
                if (event.target.checked) {
                  update([...value, { plantId: option.id, isDefault: value.length === 0, active: true, jobTitle: '' }])
                } else {
                  update(value.filter((item) => item.plantId !== option.id))
                }
              }}
            >
              {option.name}（{option.code}）
            </Checkbox>
            <Radio
              checked={defaultId === option.id}
              disabled={!row}
              onChange={() => {
                onChange(value.map((item) => ({ ...item, isDefault: item.plantId === option.id, active: item.plantId === option.id ? true : item.active })))
              }}
            >
              默认工厂
            </Radio>
            <Checkbox
              checked={row?.active ?? false}
              disabled={!row}
              onChange={(event) => {
                update(value.map((item) => (
                  item.plantId === option.id ? { ...item, active: event.target.checked } : item
                )))
              }}
            >
              在职
            </Checkbox>
            <Input
              aria-label={`${option.name}岗位`}
              placeholder="岗位"
              disabled={!row}
              value={row?.jobTitle ?? ''}
              onChange={(event) => {
                const jobTitle = event.target.value
                onChange(value.map((item) => (item.plantId === option.id ? { ...item, jobTitle } : item)))
              }}
            />
          </div>
        )
      })}
      <Form.Item label="角色">
        <Tooltip title="即将上线">
          <span className="mes-soon">
            <Select disabled placeholder="角色" style={{ width: 240 }} />
          </span>
        </Tooltip>
      </Form.Item>
      <Form.Item label="车间">
        <Tooltip title="即将上线">
          <span className="mes-soon">
            <Select disabled placeholder="车间" style={{ width: 240 }} />
          </span>
        </Tooltip>
      </Form.Item>
    </div>
  )
}

function initialBindings(mode: EditorMode, plants: PlantChoice[]): BindingDraft[] {
  if (mode !== 'create' || plants.length !== 1) return []
  const only = plants[0]
  return [{ plantId: only.id, isDefault: true, active: true, jobTitle: '' }]
}

function toDraft(plant: PlantBinding): BindingDraft {
  return {
    plantId: plant.plantId,
    isDefault: plant.isDefault,
    active: plant.active,
    jobTitle: plant.jobTitle ?? '',
  }
}

function normalizeBindings(rows: BindingDraft[]): PlantBindingInput[] {
  return rows.map((row) => ({
    plantId: row.plantId,
    isDefault: row.isDefault,
    active: row.active,
    jobTitle: row.jobTitle.trim() || null,
    roleIds: [],
  }))
}

function blank(value: string): string | null {
  const text = value.trim()
  return text ? text : null
}
