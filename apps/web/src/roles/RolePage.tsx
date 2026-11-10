import { DownloadOutlined, PlusOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tooltip,
} from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import dayjs from 'dayjs'
import { menuTree, type MenuNode } from '../api/menus.ts'
import { formatProblem, isAuthRedirect, isStale, toApiError, type ApiError } from '../api/problem.ts'
import { getPlantEpoch } from '../api/plant-session.ts'
import {
  createRole,
  deleteRole,
  listRoles,
  replaceRoleMenus,
  roleMenuIds,
  updateRole,
  type Role,
  type RoleUpdate,
} from '../api/roles.ts'
import { Perm } from '../perm/Perm.tsx'
import { usePerm } from '../perm/usePerm.ts'
import { zhCN } from '../locale.ts'
import { resolvePage } from '../materials/query.ts'
import { sanitizeGrant } from './grant.ts'
import { DATA_SCOPES, dataScopeLabel, roleCodeProblems } from './labels.ts'
import { RoleGrantTree } from './RoleGrantTree.tsx'
import { DEFAULT_ROLE_QUERY, roleSort, toRoleParams, type RoleListQuery } from './query.ts'

function columnSortOrder(field: string, sort: string): 'ascend' | 'descend' | undefined {
  const [name, direction] = sort.split(',')
  if (name !== field) return undefined
  return direction === 'desc' ? 'descend' : 'ascend'
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : value
}

function roleBody(row: Role, patch: Partial<RoleUpdate> = {}): RoleUpdate {
  return {
    name: row.name,
    remark: row.remark,
    dataScope: row.dataScope,
    sortNo: row.sortNo,
    enabled: row.enabled,
    lockVersion: row.lockVersion,
    ...patch,
  }
}

type EditorState = { mode: 'create' | 'edit'; role?: Role } | null

export function RolePage() {
  const { message } = App.useApp()
  const [draft, setDraft] = useState('')
  const [applied, setApplied] = useState<RoleListQuery>(DEFAULT_ROLE_QUERY)
  const [reloadToken, setReloadToken] = useState(0)
  const requestKey = `${applied.page}|${applied.size}|${applied.sort}|${applied.q}|${reloadToken}`
  const [settled, setSettled] = useState<{
    key: string
    rows: Role[]
    total: number
    error: ApiError | null
  } | null>(null)
  const loading = settled?.key !== requestKey
  const error = settled?.key === requestKey ? settled.error : null
  const rows = settled?.rows ?? []
  const total = settled?.total ?? 0
  const [acting, setActing] = useState(false)
  const [editor, setEditor] = useState<EditorState>(null)
  const [grantRole, setGrantRole] = useState<Role | null>(null)

  useEffect(() => {
    const epoch = getPlantEpoch()
    let cancel = false
    const key = requestKey
    listRoles(toRoleParams(applied))
      .then((page) => {
        if (cancel || getPlantEpoch() !== epoch) return
        const nextPage = resolvePage(applied.page, applied.size, page.total)
        if (nextPage !== applied.page) {
          setApplied((prev) => (prev.page === applied.page ? { ...prev, page: nextPage } : prev))
          return
        }
        setSettled({ key, rows: page.items, total: page.total, error: null })
      })
      .catch((err: unknown) => {
        if (cancel || isStale(err) || isAuthRedirect(err) || getPlantEpoch() !== epoch) return
        setSettled({ key, rows: [], total: 0, error: toApiError(err) })
      })
    return () => {
      cancel = true
    }
  }, [applied, reloadToken, requestKey])

  function reload() {
    setReloadToken((value) => value + 1)
  }

  async function run(task: () => Promise<void>) {
    setActing(true)
    try {
      await task()
      reload()
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setActing(false)
    }
  }

  const columns: TableProps<Role>['columns'] = [
    {
      title: '编码',
      dataIndex: 'code',
      width: 140,
      sorter: true,
      sortOrder: columnSortOrder('code', applied.sort),
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 160,
      sorter: true,
      sortOrder: columnSortOrder('name', applied.sort),
    },
    {
      title: '数据范围',
      dataIndex: 'dataScope',
      width: 140,
      render: (value: string) => dataScopeLabel(value),
    },
    {
      title: '排序',
      dataIndex: 'sortNo',
      width: 90,
      sorter: true,
      sortOrder: columnSortOrder('sortNo', applied.sort),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value: boolean) => (
        <span className={`mes-status-tag is-${value ? 'released' : 'stopped'}`}>
          <span className="mes-status-dot" />
          {value ? '启用' : '停用'}
        </span>
      ),
    },
    {
      title: '内置',
      dataIndex: 'builtin',
      width: 80,
      render: (value: boolean) => (value ? '是' : '否'),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      render: (value: string | undefined) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      fixed: 'right',
      render: (_value, row) => (
        <Space size={0} className="mes-row-actions">
          <Perm code="sys:role:grant" global>
            <Button type="link" size="small" disabled={acting} onClick={() => setGrantRole(row)}>授权</Button>
          </Perm>
          <Perm code="sys:role:update" global>
            <Button type="link" size="small" disabled={acting} onClick={() => setEditor({ mode: 'edit', role: row })}>
              编辑
            </Button>
          </Perm>
          {row.enabled ? (
            <Perm code="sys:role:update" global>
              <Popconfirm
                title="停用该角色？"
                okText="停用"
                cancelText="取消"
                onConfirm={() => {
                  void run(async () => {
                    await updateRole(row.id, roleBody(row, { enabled: false }))
                    message.success('已停用')
                  })
                }}
              >
                <Button type="link" size="small" disabled={acting}>停用</Button>
              </Popconfirm>
            </Perm>
          ) : (
            <Perm code="sys:role:update" global>
              <Button
                type="link"
                size="small"
                disabled={acting}
                onClick={() => {
                  void run(async () => {
                    await updateRole(row.id, roleBody(row, { enabled: true }))
                    message.success('已启用')
                  })
                }}
              >
                启用
              </Button>
            </Perm>
          )}
          <Perm code="sys:role:delete" global>
            <Popconfirm
              title="删除该角色？"
              okText="删除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
              onConfirm={() => {
                void run(async () => {
                  await deleteRole(row.id, row.lockVersion)
                  message.success('已删除')
                })
              }}
            >
              <Button type="link" size="small" danger disabled={acting}>删除</Button>
            </Popconfirm>
          </Perm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card className="mes-filter-card">
        <Form layout="inline" className="mes-filter-form">
          <Form.Item label="关键词">
            <Input
              aria-label="关键词"
              allowClear
              placeholder="编码 / 名称"
              style={{ width: 220 }}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
            />
          </Form.Item>
          <Form.Item className="mes-filter-actions">
            <Space>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={() => setApplied((prev) => ({ ...prev, page: 1, q: draft }))}
              >
                查询
              </Button>
              <Button
                onClick={() => {
                  setDraft('')
                  setApplied((prev) => ({ ...prev, page: 1, q: '' }))
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
      <Card className="mes-table-card">
        <div className="mes-actions">
          <Space wrap>
            <Perm code="sys:role:create" global>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditor({ mode: 'create' })}>新增</Button>
            </Perm>
            <Tooltip title="即将上线">
              <span className="mes-soon">
                <Button disabled icon={<UploadOutlined />}>批量导入</Button>
              </span>
            </Tooltip>
            <Tooltip title="即将上线">
              <span className="mes-soon">
                <Button disabled icon={<DownloadOutlined />}>导出</Button>
              </span>
            </Tooltip>
          </Space>
        </div>
        {error ? (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 12 }}
            message={error.problem.title}
            description={
              <div>
                <div style={{ whiteSpace: 'pre-wrap' }}>{formatProblem(error.problem)}</div>
                <Button size="small" style={{ marginTop: 8 }} onClick={reload}>重试</Button>
              </div>
            }
          />
        ) : null}
        <Table<Role>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1140 }}
          pagination={{
            locale: zhCN.Pagination,
            current: applied.page,
            pageSize: applied.size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (count) => `共 ${count} 条记录`,
          }}
          locale={{ emptyText: loading ? '正在加载' : '暂无角色' }}
          onChange={(pagination, _filters, sorter) => {
            const current = Array.isArray(sorter) ? sorter[0] : sorter
            const nextSize = pagination.pageSize ?? applied.size
            const requested = nextSize !== applied.size ? 1 : (pagination.current ?? 1)
            const nextPage = resolvePage(requested, nextSize, total)
            const sort = roleSort(current?.field, current?.order)
            setApplied((prev) => {
              if (prev.page === nextPage && prev.size === nextSize && prev.sort === sort) return prev
              return { ...prev, page: nextPage, size: nextSize, sort }
            })
          }}
        />
      </Card>
      {editor ? (
        <RoleEditor
          key={`${editor.mode}:${editor.role?.id ?? ''}`}
          open
          mode={editor.mode}
          role={editor.role ?? null}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null)
            reload()
          }}
        />
      ) : null}
      {grantRole ? (
        <GrantDrawer
          key={grantRole.id}
          role={grantRole}
          onClose={() => setGrantRole(null)}
          onChanged={(next) => {
            setGrantRole(next)
            reload()
          }}
        />
      ) : null}
    </>
  )
}

type RoleFormValues = {
  code?: string
  name: string
  remark?: string
  dataScope: string
  sortNo?: number | null
  enabled: boolean
}

function RoleEditor({
  open,
  mode,
  role,
  onClose,
  onSaved,
}: {
  open: boolean
  mode: 'create' | 'edit'
  role: Role | null
  onClose: () => void
  onSaved: () => void
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<RoleFormValues>()
  const [saving, setSaving] = useState(false)

  async function save() {
    const values = await form.validateFields()
    const name = values.name.trim()
    if (!name) {
      message.error('请输入名称')
      return
    }
    if ([...name].length > 64) {
      message.error('名称最长 64 字')
      return
    }
    const remark = values.remark?.trim() ? values.remark.trim() : null
    if (remark && [...remark].length > 500) {
      message.error('备注最长 500 字')
      return
    }
    setSaving(true)
    try {
      if (mode === 'create') {
        const code = (values.code ?? '').trim().toUpperCase()
        const problems = roleCodeProblems(code)
        if (problems.length > 0) {
          message.error(problems.join('；'))
          setSaving(false)
          return
        }
        await createRole({
          code,
          name,
          remark,
          dataScope: values.dataScope,
          sortNo: values.sortNo ?? 0,
          enabled: values.enabled,
        })
        message.success('已创建')
      } else if (role) {
        await updateRole(role.id, {
          name,
          remark,
          dataScope: values.dataScope,
          sortNo: values.sortNo ?? role.sortNo,
          enabled: values.enabled,
          lockVersion: role.lockVersion,
        })
        message.success('已保存')
      }
      onSaved()
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Drawer
      open={open}
      width={560}
      title={mode === 'create' ? '新增角色' : '编辑角色'}
      destroyOnHidden
      onClose={onClose}
      extra={(
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={() => { void save() }}>保存</Button>
        </Space>
      )}
    >
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{
          code: role?.code ?? '',
          name: role?.name ?? '',
          remark: role?.remark ?? '',
          dataScope: role?.dataScope || 'PLANT',
          sortNo: role?.sortNo ?? 0,
          enabled: role?.enabled ?? true,
        }}
      >
        <Form.Item label="编码" name="code" rules={mode === 'create' ? [{ required: true, message: '请输入编码' }] : undefined}>
          <Input autoComplete="off" disabled={mode === 'edit'} />
        </Form.Item>
        <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item label="备注" name="remark">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item label="数据范围" name="dataScope">
          <Select aria-label="数据范围" virtual={false} options={[...DATA_SCOPES]} />
        </Form.Item>
        <Form.Item label="排序" name="sortNo">
          <InputNumber min={0} precision={0} style={{ width: 160 }} />
        </Form.Item>
        <Form.Item label="启用" name="enabled" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

function GrantDrawer({
  role,
  onClose,
  onChanged,
}: {
  role: Role
  onClose: () => void
  onChanged: (next: Role) => void
}) {
  const { message } = App.useApp()
  const canUpdate = usePerm('sys:role:update')
  const [tab, setTab] = useState('grant')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [menus, setMenus] = useState<MenuNode[]>([])
  const [checked, setChecked] = useState<string[]>([])
  const [current, setCurrent] = useState(role)
  const [scope, setScope] = useState(role.dataScope)

  useEffect(() => {
    const epoch = getPlantEpoch()
    let cancel = false
    Promise.all([menuTree(), roleMenuIds(role.id)])
      .then(([tree, ids]) => {
        if (cancel || getPlantEpoch() !== epoch) return
        setMenus(tree)
        setChecked(sanitizeGrant(ids, tree))
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (cancel || isStale(err) || isAuthRedirect(err) || getPlantEpoch() !== epoch) return
        setError(toApiError(err))
        setLoading(false)
      })
    return () => {
      cancel = true
    }
  }, [role.id])

  async function saveGrant() {
    setSaving(true)
    try {
      const ids = await replaceRoleMenus(current.id, checked)
      setChecked(sanitizeGrant(ids, menus))
      message.success('已保存')
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setSaving(false)
    }
  }

  async function saveScope() {
    setSaving(true)
    try {
      const updated = await updateRole(current.id, roleBody(current, { dataScope: scope }))
      setCurrent(updated)
      setScope(updated.dataScope)
      onChanged(updated)
      message.success('已保存')
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Drawer
      open
      width={720}
      title={`授权 ${current.name}`}
      destroyOnHidden
      onClose={onClose}
      extra={tab === 'grant' ? (
        <Perm code="sys:role:grant" global>
          <Button type="primary" loading={saving} disabled={loading || error !== null} onClick={() => { void saveGrant() }}>
            保存
          </Button>
        </Perm>
      ) : (
        <Perm code="sys:role:update" global>
          <Button type="primary" loading={saving} onClick={() => { void saveScope() }}>保存</Button>
        </Perm>
      )}
    >
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'grant',
            label: '功能权限',
            children: (
              <>
                {error ? (
                  <Alert
                    type="error"
                    showIcon
                    message={error.problem.title}
                    description={formatProblem(error.problem)}
                  />
                ) : null}
                {loading ? <div>正在加载权限</div> : null}
                {!loading && !error ? (
                  <RoleGrantTree menus={menus} value={checked} onChange={setChecked} />
                ) : null}
              </>
            ),
          },
          {
            key: 'scope',
            label: '数据范围',
            children: (
              <Form layout="vertical" requiredMark={false}>
                <Form.Item label="数据范围">
                  {canUpdate ? (
                    <Select
                      aria-label="数据范围"
                      virtual={false}
                      value={scope}
                      options={[...DATA_SCOPES]}
                      onChange={setScope}
                    />
                  ) : (
                    <span>{dataScopeLabel(scope)}</span>
                  )}
                </Form.Item>
                <Form.Item label="部门">
                  <Tooltip title="即将上线">
                    <span className="mes-soon">
                      <Select disabled mode="multiple" placeholder="部门" style={{ width: '100%' }} />
                    </span>
                  </Tooltip>
                </Form.Item>
              </Form>
            ),
          },
        ]}
      />
    </Drawer>
  )
}
