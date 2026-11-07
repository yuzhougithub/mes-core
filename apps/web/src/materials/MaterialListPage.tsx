import { Alert, App, Button, Card, DatePicker, Form, Input, Popconfirm, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import dayjs from 'dayjs'
import { deleteMaterial, listMaterials, listUoms } from '../api/resources.ts'
import { formatProblem, isStale, toApiError, type ApiError } from '../api/problem.ts'
import { getPlantEpoch } from '../api/plant-session.ts'
import type { Material, Uom } from '../api/types.ts'
import { usePlant } from '../plant/PlantProvider.tsx'
import { setMaterialsEnabled } from './batch-enabled.ts'
import { MATERIAL_STATUSES, statusMeta } from './constants.ts'
import { MaterialDrawer, type DrawerMode } from './MaterialDrawer.tsx'
import { DEFAULT_MATERIAL_QUERY, materialSort, toMaterialParams, type MaterialListQuery } from './query.ts'

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

function StatusTag({ status }: { status: string }) {
  const meta = statusMeta(status)
  return (
    <Tag color={meta.tone} className="mes-status-tag">
      <span className={`mes-status-dot is-${meta.tone}`} />
      {meta.label}
    </Tag>
  )
}

type DrawerState = { mode: DrawerMode; id?: string } | null

function MaterialList() {
  const { message, modal } = App.useApp()
  const [draft, setDraft] = useState({ q: '', status: '' })
  const [applied, setApplied] = useState<MaterialListQuery>(DEFAULT_MATERIAL_QUERY)
  const [rows, setRows] = useState<Material[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [selected, setSelected] = useState<string[]>([])
  const [acting, setActing] = useState(false)
  const [uoms, setUoms] = useState<Uom[]>([])
  const [uomTruncated, setUomTruncated] = useState(false)
  const [drawer, setDrawer] = useState<DrawerState>(null)

  useEffect(() => {
    const epoch = getPlantEpoch()
    let cancelled = false
    setLoading(true)
    setError(null)
    listMaterials(toMaterialParams(applied))
      .then((page) => {
        if (cancelled || getPlantEpoch() !== epoch) return
        setRows(page.items)
        setTotal(page.total)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (cancelled || isStale(err) || getPlantEpoch() !== epoch) return
        setRows([])
        setTotal(0)
        setError(toApiError(err))
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [applied, reloadToken])

  useEffect(() => {
    const epoch = getPlantEpoch()
    let cancelled = false
    listUoms({ enabled: true, size: 200 })
      .then((page) => {
        if (cancelled || getPlantEpoch() !== epoch) return
        setUoms(page.items)
        setUomTruncated(page.total > page.items.length)
      })
      .catch((err: unknown) => {
        if (cancelled || isStale(err)) return
        setUoms([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  function uomLabel(id: string): string {
    const uom = uoms.find((item) => item.id === id)
    if (!uom) return uomTruncated ? `${id}（单位列表不完整）` : id
    return `${uom.name}（${uom.code}）`
  }

  async function runBatch(enabled: boolean) {
    setActing(true)
    try {
      const result = await setMaterialsEnabled(selected, enabled)
      const verb = enabled ? '启用' : '停用'
      if (result.failures.length === 0) {
        message.success(`已${verb} ${result.updated} 条，跳过 ${result.skipped} 条（原本已是该状态）`)
      } else {
        modal.warning({
          title: `批量${verb}未全部完成`,
          content: (
            <div>
              <div>成功 {result.updated} 条，跳过 {result.skipped} 条，失败 {result.failures.length} 条。</div>
              <ul>
                {result.failures.map((failure) => (
                  <li key={failure.id}>{failure.message}（{failure.code}）</li>
                ))}
              </ul>
            </div>
          ),
        })
      }
      setSelected([])
      setReloadToken((value) => value + 1)
    } catch (err) {
      if (!isStale(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setActing(false)
    }
  }

  async function disableOne(row: Material) {
    setActing(true)
    try {
      const result = await setMaterialsEnabled([row.id], false)
      if (result.failures.length) {
        message.error(`${result.failures[0].message}（${result.failures[0].code}）`)
      } else if (result.skipped) {
        message.info('当前工厂已是停用，未重复提交')
      } else {
        message.success('已在当前工厂停用')
      }
      setReloadToken((value) => value + 1)
    } catch (err) {
      if (!isStale(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setActing(false)
    }
  }

  async function remove(row: Material) {
    setActing(true)
    try {
      await deleteMaterial(row.id, row.lockVersion)
      message.success('已删除')
      setSelected((current) => current.filter((id) => id !== row.id))
      setReloadToken((value) => value + 1)
    } catch (err) {
      if (!isStale(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setActing(false)
    }
  }

  const columns: TableProps<Material>['columns'] = [
    { title: '编码', dataIndex: 'code', width: 150, sorter: true, sortOrder: columnSortOrder('code', applied.sort) },
    { title: '名称', dataIndex: 'name', width: 140, sorter: true, sortOrder: columnSortOrder('name', applied.sort) },
    {
      title: '规格',
      dataIndex: 'spec',
      width: 160,
      render: (value: string | null | undefined) => value || '—',
    },
    {
      title: uomTruncated ? '单位（列表不完整）' : '单位',
      dataIndex: 'uomId',
      width: 120,
      render: (value: string) => uomLabel(value),
    },
    {
      title: (
        <Tooltip title="分类名称接口 GET /api/v1/material-categories 未实现。有分类时显示分类 ID。">
          <span>分类</span>
        </Tooltip>
      ),
      dataIndex: 'categoryId',
      width: 120,
      render: (value: string | null | undefined) => value || '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      sorter: true,
      sortOrder: columnSortOrder('status', applied.sort),
      render: (value: string) => <StatusTag status={value} />,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      sorter: true,
      sortOrder: columnSortOrder('updatedAt', applied.sort),
      render: (value: string | undefined) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 210,
      render: (_value, row) => (
        <Space size={0} className="mes-row-actions">
          <Button type="link" size="small" onClick={() => setDrawer({ mode: 'detail', id: row.id })}>详情</Button>
          <Button type="link" size="small" onClick={() => setDrawer({ mode: 'edit', id: row.id })}>编辑</Button>
          {row.status === 'OBSOLETE' ? (
            <Tooltip title="已作废，本厂扩展只读">
              <Button type="link" size="small" disabled>停用</Button>
            </Tooltip>
          ) : (
            <Popconfirm
              title="在当前工厂停用？"
              description="只改本厂扩展 enabled，不改变草稿、发布或作废。已是停用则不会重复提交。"
              okText="停用"
              cancelText="取消"
              onConfirm={() => disableOne(row)}
            >
              <Button type="link" size="small">停用</Button>
            </Popconfirm>
          )}
          {row.status === 'DRAFT' ? (
            <Popconfirm
              title="删除该草稿物料？"
              description="仅草稿可删除，服务端做逻辑删除。"
              okText="删除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
              onConfirm={() => remove(row)}
            >
              <Button type="link" size="small" danger>删除</Button>
            </Popconfirm>
          ) : (
            <Tooltip title="只有草稿可删除">
              <Button type="link" size="small" danger disabled>删除</Button>
            </Tooltip>
          )}
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
              style={{ width: 200 }}
              value={draft.q}
              onChange={(event) => setDraft((prev) => ({ ...prev, q: event.target.value }))}
            />
          </Form.Item>
          <Form.Item label="分类">
            <Tooltip title="未实现：GET /api/v1/material-categories。列表虽然接受 categoryId，但没有分类目录，不能做下拉，也不会只过滤当前页。">
              <span data-testid="filter-category">
                <Select disabled placeholder="分类接口未实现" style={{ width: 180 }} />
              </span>
            </Tooltip>
          </Form.Item>
          <Form.Item label="状态">
            <Select
              aria-label="状态"
              allowClear
              virtual={false}
              placeholder="全部"
              style={{ width: 140 }}
              value={draft.status || undefined}
              options={MATERIAL_STATUSES}
              onChange={(value) => setDraft((prev) => ({ ...prev, status: value ?? '' }))}
            />
          </Form.Item>
          <Form.Item label="创建时间">
            <Tooltip title="未实现：GET /api/v1/materials 没有创建时间范围参数。不会提交日期，也不会只过滤当前页。">
              <span data-testid="filter-created-range">
                <DatePicker.RangePicker
                  disabled
                  allowEmpty={[true, true]}
                  placeholder={['开始日期', '结束日期']}
                  style={{ width: 260 }}
                />
              </span>
            </Tooltip>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button
                type="primary"
                onClick={() => {
                  setApplied((prev) => ({ ...prev, page: 1, q: draft.q, status: draft.status }))
                  setSelected([])
                }}
              >
                查询
              </Button>
              <Button
                onClick={() => {
                  setDraft({ q: '', status: '' })
                  setApplied((prev) => ({ ...prev, page: 1, q: '', status: '' }))
                  setSelected([])
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
        <Typography.Paragraph type="secondary" className="mes-filter-note">
          分类与创建时间暂不可筛：对应接口或查询参数未提供，查询不会提交它们，也不会在当前页本地过滤。批量导入与导出未实现，按钮已禁用。
        </Typography.Paragraph>
      </Card>

      <Card className="mes-table-card">
        <div className="mes-actions">
          <Space wrap>
            <Button type="primary" onClick={() => setDrawer({ mode: 'create' })}>新增</Button>
            <Tooltip title="未实现：GET /api/v1/io/materials/template 与 POST /api/v1/io/materials/import。不会假装导入成功。">
              <span>
                <Button disabled data-testid="btn-import">批量导入</Button>
              </span>
            </Tooltip>
            <Tooltip title="未实现：GET /api/v1/io/materials/export。分页列表不能当作全量导出。">
              <span>
                <Button disabled data-testid="btn-export">导出</Button>
              </span>
            </Tooltip>
            <Popconfirm
              title="批量启用所选物料？"
              description="没有批量接口。将逐条 GET 详情后 PUT /materials/{id}/plant-ext。切换工厂会中止后续请求。已作废的条目会失败并列出。"
              okText="启用"
              cancelText="取消"
              disabled={selected.length === 0}
              onConfirm={() => runBatch(true)}
            >
              <Button disabled={selected.length === 0 || acting} data-testid="btn-enable">批量启用</Button>
            </Popconfirm>
            <Popconfirm
              title="批量停用所选物料？"
              description="没有批量接口。将逐条写本厂扩展 enabled=false，不改变生命周期状态。已作废的条目会失败并列出。"
              okText="停用"
              cancelText="取消"
              disabled={selected.length === 0}
              onConfirm={() => runBatch(false)}
            >
              <Button disabled={selected.length === 0 || acting} data-testid="btn-disable">批量停用</Button>
            </Popconfirm>
          </Space>
          <Space>
            <span>已选 {selected.length} 项</span>
            <Button type="link" disabled={selected.length === 0} onClick={() => setSelected([])}>清空</Button>
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
        <Table<Material>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          rowSelection={{
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys.map(String)),
          }}
          pagination={{
            current: applied.page,
            pageSize: applied.size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100, 200],
            showTotal: (count) => `共 ${count} 条`,
          }}
          locale={{ emptyText: loading ? '正在加载' : '当前工厂暂无物料' }}
          onChange={(pagination, _filters, sorter) => {
            const current = Array.isArray(sorter) ? sorter[0] : sorter
            const nextSize = pagination.pageSize ?? applied.size
            const nextPage = nextSize !== applied.size ? 1 : (pagination.current ?? 1)
            const sort = materialSort(current?.field, current?.order)
            setApplied((prev) => {
              if (prev.page === nextPage && prev.size === nextSize && prev.sort === sort) return prev
              return { ...prev, page: nextPage, size: nextSize, sort }
            })
          }}
        />
      </Card>
      {drawer ? (
        <MaterialDrawer
          open
          mode={drawer.mode}
          materialId={drawer.id}
          onClose={() => setDrawer(null)}
          onSaved={() => {
            setDrawer(null)
            setReloadToken((value) => value + 1)
          }}
          onPartialCreate={(id) => {
            setReloadToken((value) => value + 1)
            setDrawer({ mode: 'edit', id })
          }}
        />
      ) : null}
    </>
  )
}

export function MaterialListPage() {
  const { plantId, loading, error } = usePlant()
  if (!plantId) {
    return (
      <Alert
        type="warning"
        showIcon
        message={loading ? '正在加载工厂' : '没有选中的工厂'}
        description={error ? formatProblem(error.problem) : '请在左侧选择工厂。若列表为空，需要先创建工厂后再查物料。'}
      />
    )
  }
  return <MaterialList key={plantId} />
}
