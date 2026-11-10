import {
  DownloadOutlined,
  PlusOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Card, Form, Input, Popconfirm, Select, Space, Table, Tooltip } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import dayjs from 'dayjs'
import { listPlants } from '../api/resources.ts'
import { formatProblem, isAuthRedirect, isStale, toApiError, type ApiError } from '../api/problem.ts'
import { getPlantEpoch, getPlantSnapshot, subscribePlantSession } from '../api/plant-session.ts'
import {
  contactKnown,
  deleteUser,
  disableUser,
  enableUser,
  getUser,
  listUsers,
  resetUserPassword,
  userEmail,
  userMobile,
  type UserSummary,
} from '../api/users.ts'
import { maskEmail, maskMobile } from '../auth/mask.ts'
import { passwordProblems } from '../auth/password.ts'
import { recoverPlantAccess } from '../auth/store.ts'
import { useAuth } from '../auth/useAuth.ts'
import { zhCN } from '../locale.ts'
import { resolvePage } from '../materials/query.ts'
import { OneTimeSecret } from './OneTimeSecret.tsx'
import { UserEditor, type EditorMode, type PlantChoice } from './UserEditor.tsx'
import { DEFAULT_USER_QUERY, toUserParams, userSort, type UserListQuery } from './query.ts'

const NO_EXTRA: PlantChoice[] = []
const NO_PLANTS: PlantChoice[] = []

const STATUSES = [
  { value: 'ACTIVE', label: '启用' },
  { value: 'DISABLED', label: '停用' },
]

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

type DrawerState = { mode: EditorMode; id?: string } | null

export function UserListPage({
  extraPlants = NO_EXTRA,
  recoverWhenSelfAssigned = false,
}: {
  extraPlants?: PlantChoice[]
  recoverWhenSelfAssigned?: boolean
}) {
  const { message } = App.useApp()
  const auth = useAuth()
  const plantSession = useSyncExternalStore(subscribePlantSession, getPlantSnapshot, getPlantSnapshot)
  const epoch = plantSession.epoch
  const plantId = plantSession.plantId
  const superAdmin = auth.me?.user.superAdmin === true
  const [draft, setDraft] = useState({ q: '', status: '' })
  const [applied, setApplied] = useState<UserListQuery>(DEFAULT_USER_QUERY)
  const [reloadToken, setReloadToken] = useState(0)
  const requestKey = `${epoch}|${applied.page}|${applied.size}|${applied.sort}|${applied.q}|${applied.status}|${reloadToken}`
  const [settled, setSettled] = useState<{
    key: string
    rows: UserSummary[]
    total: number
    error: ApiError | null
  } | null>(null)
  const loading = settled?.key !== requestKey
  const error = settled?.key === requestKey ? settled.error : null
  const rows = settled?.rows ?? []
  const total = settled?.total ?? 0
  const [acting, setActing] = useState(false)
  const [catalog, setCatalog] = useState<{ plantId: string; plants: PlantChoice[] } | null>(null)
  const [drawer, setDrawer] = useState<DrawerState>(null)
  const [secret, setSecret] = useState<string | null>(null)
  const passwordDraft = useRef(new Map<string, string>())

  useEffect(() => {
    const started = getPlantEpoch()
    let cancel = false
    const key = requestKey
    listUsers(toUserParams(applied))
      .then(async (page) => {
        if (cancel || getPlantEpoch() !== started) return
        const nextPage = resolvePage(applied.page, applied.size, page.total)
        if (nextPage !== applied.page) {
          setApplied((prev) => (prev.page === applied.page ? { ...prev, page: nextPage } : prev))
          return
        }
        const items = await attachContacts(page.items, started)
        if (cancel || getPlantEpoch() !== started) return
        setSettled({ key, rows: items, total: page.total, error: null })
      })
      .catch((err: unknown) => {
        if (cancel || isStale(err) || isAuthRedirect(err) || getPlantEpoch() !== started) return
        setSettled({ key, rows: [], total: 0, error: toApiError(err) })
      })
    return () => {
      cancel = true
    }
  }, [applied, reloadToken, requestKey, epoch])

  useEffect(() => {
    if (!plantId) return
    const started = getPlantEpoch()
    let cancel = false
    const current = plantId
    listPlants()
      .then((page) => {
        if (cancel || getPlantEpoch() !== started) return
        setCatalog({
          plantId: current,
          plants: page.items.map((plant) => ({ id: plant.id, code: plant.code, name: plant.name })),
        })
      })
      .catch((err: unknown) => {
        if (cancel || isStale(err) || isAuthRedirect(err)) return
        setCatalog({ plantId: current, plants: [] })
      })
    return () => {
      cancel = true
    }
  }, [plantId])

  const listedPlants = catalog && catalog.plantId === plantId ? catalog.plants : NO_PLANTS

  const plantChoices = useMemo(() => {
    const merged = new Map<string, PlantChoice>()
    for (const plant of auth.me?.plants ?? []) {
      if (!plant.id) continue
      merged.set(plant.id, { id: plant.id, code: plant.code ?? '', name: plant.name ?? plant.id })
    }
    for (const plant of listedPlants) merged.set(plant.id, plant)
    for (const plant of extraPlants) merged.set(plant.id, plant)
    const all = [...merged.values()]
    if (!superAdmin && plantId) return all.filter((plant) => plant.id === plantId)
    return all
  }, [auth.me, listedPlants, extraPlants, superAdmin, plantId])

  function plantLabel(id: string | null): string {
    if (!id) return '—'
    const found = plantChoices.find((plant) => plant.id === id)
      ?? listedPlants.find((plant) => plant.id === id)
    if (!found) return '—'
    return found.code ? `${found.name}（${found.code}）` : found.name
  }

  async function run(task: () => Promise<void>) {
    setActing(true)
    try {
      await task()
      setReloadToken((value) => value + 1)
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setActing(false)
    }
  }

  function reset(row: UserSummary) {
    const typed = superAdmin ? (passwordDraft.current.get(row.id) ?? '').trim() : ''
    passwordDraft.current.delete(row.id)
    if (typed) {
      const problems = passwordProblems(typed, row.username)
      if (problems.length) {
        message.error(problems.join('；'))
        return
      }
    }
    void run(async () => {
      const result = await resetUserPassword(row.id, row.lockVersion, typed || undefined)
      if (result.password) setSecret(result.password)
      else message.success('已重置密码')
    })
  }

  async function afterSave(userId: string, initialPassword?: string | null) {
    setDrawer(null)
    setReloadToken((value) => value + 1)
    if (initialPassword) setSecret(initialPassword)
    if (recoverWhenSelfAssigned && userId === auth.me?.user.id) {
      try {
        await recoverPlantAccess()
      } catch (err) {
        if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
      }
    }
  }

  const columns: TableProps<UserSummary>['columns'] = [
    { title: '用户名', dataIndex: 'username', width: 140, sorter: true, sortOrder: columnSortOrder('username', applied.sort) },
    {
      title: '姓名',
      dataIndex: 'personName',
      width: 120,
      render: (value: string | null) => value || '—',
    },
    {
      title: '手机号',
      key: 'mobile',
      width: 140,
      render: (_value, row) => maskMobile(userMobile(row)),
    },
    {
      title: '邮箱',
      key: 'email',
      width: 180,
      render: (_value, row) => maskEmail(userEmail(row)),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <span className={`mes-status-tag is-${value === 'DISABLED' ? 'stopped' : 'released'}`}>
          <span className="mes-status-dot" />
          {value === 'DISABLED' ? '停用' : '启用'}
        </span>
      ),
    },
    {
      title: '默认工厂',
      dataIndex: 'defaultPlantId',
      width: 160,
      render: (value: string | null) => plantLabel(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      sorter: true,
      sortOrder: columnSortOrder('updatedAt', applied.sort),
      render: (value: string | undefined) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      render: (_value, row) => (
        <Space size={0} className="mes-row-actions">
          <Button type="link" size="small" onClick={() => setDrawer({ mode: 'detail', id: row.id })}>详情</Button>
          <Button type="link" size="small" onClick={() => setDrawer({ mode: 'edit', id: row.id })}>编辑</Button>
          {row.status === 'DISABLED' ? (
            <Button type="link" size="small" disabled={acting} onClick={() => {
              void run(async () => {
                await enableUser(row.id, row.lockVersion)
                message.success('已启用')
              })
            }}
            >
              启用
            </Button>
          ) : (
            <Popconfirm title="停用该用户？" okText="停用" cancelText="取消" onConfirm={() => {
              void run(async () => {
                await disableUser(row.id, row.lockVersion)
                message.success('已停用')
              })
            }}
            >
              <Button type="link" size="small" disabled={acting}>停用</Button>
            </Popconfirm>
          )}
          <Popconfirm
            title="重置该用户的密码？"
            description={superAdmin ? (
              <div>
                <div>留空则生成临时口令，关闭后不能再查看。</div>
                <Input.Password
                  aria-label={`${row.username}的新密码`}
                  placeholder="新密码，可留空"
                  onChange={(event) => passwordDraft.current.set(row.id, event.target.value)}
                  onMouseDown={(event) => event.stopPropagation()}
                />
              </div>
            ) : '将生成临时口令，关闭后不能再查看。'}
            okText="确认重置"
            cancelText="取消"
            onConfirm={() => reset(row)}
          >
            <Button type="link" size="small" disabled={acting}>重置密码</Button>
          </Popconfirm>
          <Popconfirm
            title="删除该用户？"
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
            onConfirm={() => {
              void run(async () => {
                await deleteUser(row.id, row.lockVersion)
                message.success('已删除')
              })
            }}
          >
            <Button type="link" size="small" danger disabled={acting}>删除</Button>
          </Popconfirm>
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
              placeholder="用户名 / 工号 / 姓名"
              style={{ width: 220 }}
              value={draft.q}
              onChange={(event) => setDraft((prev) => ({ ...prev, q: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="状态">
            <Select
              aria-label="状态"
              allowClear
              virtual={false}
              placeholder="全部"
              style={{ width: 140 }}
              value={draft.status || undefined}
              options={STATUSES}
              onChange={(value) => setDraft((prev) => ({ ...prev, status: value ?? '' }))}
            />
          </Form.Item>
          <Form.Item className="mes-filter-actions">
            <Space>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={() => setApplied((prev) => ({ ...prev, page: 1, q: draft.q, status: draft.status }))}
              >
                查询
              </Button>
              <Button
                onClick={() => {
                  setDraft({ q: '', status: '' })
                  setApplied((prev) => ({ ...prev, page: 1, q: '', status: '' }))
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
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawer({ mode: 'create' })}>新增</Button>
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
                <Button size="small" style={{ marginTop: 8 }} onClick={() => setReloadToken((value) => value + 1)}>重试</Button>
              </div>
            }
          />
        ) : null}
        <Table<UserSummary>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1180 }}
          pagination={{
            locale: zhCN.Pagination,
            current: applied.page,
            pageSize: applied.size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (count) => `共 ${count} 条记录`,
          }}
          locale={{ emptyText: loading ? '正在加载' : '暂无用户' }}
          onChange={(pagination, _filters, sorter) => {
            const current = Array.isArray(sorter) ? sorter[0] : sorter
            const nextSize = pagination.pageSize ?? applied.size
            const requested = nextSize !== applied.size ? 1 : (pagination.current ?? 1)
            const nextPage = resolvePage(requested, nextSize, total)
            const sort = userSort(current?.field, current?.order)
            setApplied((prev) => {
              if (prev.page === nextPage && prev.size === nextSize && prev.sort === sort) return prev
              return { ...prev, page: nextPage, size: nextSize, sort }
            })
          }}
        />
      </Card>
      {drawer ? (
        <UserEditor
          key={`${drawer.mode}:${drawer.id ?? ''}:${plantChoices.map((plant) => plant.id).join(',')}`}
          open
          mode={drawer.mode}
          userId={drawer.id}
          superAdmin={superAdmin}
          plants={plantChoices}
          onClose={() => setDrawer(null)}
          onSaved={(userId, initialPassword) => { void afterSave(userId, initialPassword) }}
        />
      ) : null}
      <OneTimeSecret password={secret} onClose={() => setSecret(null)} />
    </>
  )
}

async function attachContacts(rows: UserSummary[], epoch: number): Promise<UserSummary[]> {
  return Promise.all(rows.map(async (row) => {
    if (contactKnown(row)) return row
    try {
      const detail = await getUser(row.id)
      if (getPlantEpoch() !== epoch) return row
      return {
        ...row,
        mobile: detail.person?.mobile ?? null,
        email: detail.person?.email ?? null,
        lockVersion: detail.lockVersion ?? row.lockVersion,
      }
    } catch (err) {
      if (isStale(err) || isAuthRedirect(err)) throw err
      return row
    }
  }))
}
