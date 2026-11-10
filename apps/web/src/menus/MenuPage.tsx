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
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tooltip,
  TreeSelect,
} from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import {
  createMenu,
  deleteMenu,
  menuTree,
  updateMenu,
  type MenuNode,
  type MenuWrite,
} from '../api/menus.ts'
import { formatProblem, isAuthRedirect, isStale, toApiError, type ApiError } from '../api/problem.ts'
import { getPlantEpoch } from '../api/plant-session.ts'
import { Perm } from '../perm/Perm.tsx'
import {
  MENU_TYPES,
  ROOT_PARENT,
  cleanMenuWrite,
  expandableIds,
  filterMenuTree,
  findMenu,
  menuProblems,
  menuTypeLabel,
  parentChoices,
  parentProblems,
  suggestChildType,
  toWrite,
} from './tree.ts'

const EMPTY_MENUS: MenuNode[] = []

type EditorState = { mode: 'create' | 'edit'; parentId: string | null; id?: string } | null

type Row = Omit<MenuNode, 'children'> & { children?: Row[] }

function rowsOf(nodes: MenuNode[]): Row[] {
  return nodes.map((node) => ({
    ...node,
    children: node.children.length > 0 ? rowsOf(node.children) : undefined,
  }))
}

export function MenuPage() {
  const { message } = App.useApp()
  const [draft, setDraft] = useState('')
  const [applied, setApplied] = useState('')
  const [reloadToken, setReloadToken] = useState(0)
  const [settled, setSettled] = useState<{ key: number; rows: MenuNode[]; error: ApiError | null } | null>(null)
  const loading = settled?.key !== reloadToken
  const error = settled?.key === reloadToken ? settled.error : null
  const tree = settled?.rows ?? EMPTY_MENUS
  const [acting, setActing] = useState(false)
  const [editor, setEditor] = useState<EditorState>(null)
  const [moving, setMoving] = useState<MenuNode | null>(null)
  const shown = useMemo(() => filterMenuTree(tree, applied), [tree, applied])
  const expandKey = useMemo(() => expandableIds(shown).join('|'), [shown])
  const [expandedState, setExpandedState] = useState<{ key: string; keys: string[] } | null>(null)
  const expanded = expandedState?.key === expandKey
    ? expandedState.keys
    : (expandKey ? expandKey.split('|') : [])

  useEffect(() => {
    const epoch = getPlantEpoch()
    let cancel = false
    const key = reloadToken
    menuTree()
      .then((rows) => {
        if (cancel || getPlantEpoch() !== epoch) return
        setSettled({ key, rows, error: null })
      })
      .catch((err: unknown) => {
        if (cancel || isStale(err) || isAuthRedirect(err) || getPlantEpoch() !== epoch) return
        setSettled({ key, rows: [], error: toApiError(err) })
      })
    return () => {
      cancel = true
    }
  }, [reloadToken])

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

  const columns: TableProps<Row>['columns'] = [
    { title: '名称', dataIndex: 'name', width: 200 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 80,
      render: (value: string) => menuTypeLabel(value),
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
      title: '权限点',
      dataIndex: 'permCode',
      width: 190,
      render: (value: string | null) => value || '—',
    },
    {
      title: '路由',
      dataIndex: 'path',
      width: 140,
      render: (value: string | null) => value || '—',
    },
    {
      title: '组件',
      dataIndex: 'component',
      width: 130,
      render: (value: string | null) => value || '—',
    },
    { title: '排序', dataIndex: 'sortNo', width: 70 },
    {
      title: '显示',
      dataIndex: 'visible',
      width: 70,
      render: (value: boolean) => (value ? '显示' : '隐藏'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 248,
      fixed: 'right',
      render: (_value, row) => {
        const node = findMenu(tree, row.id)
        if (!node) return null
        return (
          <Space size={0} className="mes-row-actions">
            {node.type === 'BUTTON' ? null : (
              <Perm code="sys:menu:create">
                <Button
                  type="link"
                  size="small"
                  disabled={acting}
                  onClick={() => setEditor({ mode: 'create', parentId: node.id })}
                >
                  新增
                </Button>
              </Perm>
            )}
            <Perm code="sys:menu:update">
              <Button
                type="link"
                size="small"
                disabled={acting}
                onClick={() => setEditor({ mode: 'edit', parentId: node.parentId, id: node.id })}
              >
                编辑
              </Button>
            </Perm>
            <Perm code="sys:menu:update">
              <Button type="link" size="small" disabled={acting} onClick={() => setMoving(node)}>移动</Button>
            </Perm>
            {node.enabled ? (
              <Perm code="sys:menu:update">
                <Popconfirm
                  title="停用该菜单？"
                  okText="停用"
                  cancelText="取消"
                  onConfirm={() => {
                    void run(async () => {
                      await updateMenu(node.id, toWrite(node, { enabled: false }))
                      message.success('已停用')
                    })
                  }}
                >
                  <Button type="link" size="small" disabled={acting}>停用</Button>
                </Popconfirm>
              </Perm>
            ) : (
              <Perm code="sys:menu:update">
                <Button
                  type="link"
                  size="small"
                  disabled={acting}
                  onClick={() => {
                    void run(async () => {
                      await updateMenu(node.id, toWrite(node, { enabled: true }))
                      message.success('已启用')
                    })
                  }}
                >
                  启用
                </Button>
              </Perm>
            )}
            <Perm code="sys:menu:delete">
              <Popconfirm
                title="删除该菜单？"
                okText="删除"
                okButtonProps={{ danger: true }}
                cancelText="取消"
                onConfirm={() => {
                  void run(async () => {
                    await deleteMenu(node.id, node.lockVersion)
                    message.success('已删除')
                  })
                }}
              >
                <Button type="link" size="small" danger disabled={acting}>删除</Button>
              </Popconfirm>
            </Perm>
          </Space>
        )
      },
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
              placeholder="名称 / 权限点 / 路由"
              style={{ width: 240 }}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
            />
          </Form.Item>
          <Form.Item className="mes-filter-actions">
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={() => setApplied(draft)}>查询</Button>
              <Button onClick={() => { setDraft(''); setApplied('') }}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
      <Card className="mes-table-card">
        <div className="mes-actions">
          <Space wrap>
            <Perm code="sys:menu:create">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditor({ mode: 'create', parentId: null })}>
                新增
              </Button>
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
        <Table<Row>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rowsOf(shown)}
          scroll={{ x: 1218 }}
          pagination={false}
          expandable={{
            expandedRowKeys: expanded,
            onExpandedRowsChange: (keys) => setExpandedState({ key: expandKey, keys: keys.map(String) }),
          }}
          locale={{ emptyText: loading ? '正在加载' : '暂无菜单' }}
        />
      </Card>
      {editor ? (
        <MenuEditor
          key={`${editor.mode}:${editor.id ?? editor.parentId ?? 'root'}`}
          open
          mode={editor.mode}
          node={editor.id ? findMenu(tree, editor.id) : null}
          parentId={editor.parentId}
          nodes={tree}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null)
            reload()
          }}
        />
      ) : null}
      {moving ? (
        <MoveMenu
          key={moving.id}
          node={moving}
          nodes={tree}
          onClose={() => setMoving(null)}
          onMoved={() => {
            setMoving(null)
            reload()
          }}
        />
      ) : null}
    </>
  )
}

type MenuFormValues = {
  type: string
  name: string
  parentKey?: string
  permCode?: string
  path?: string
  component?: string
  icon?: string
  sortNo?: number | null
  visible: boolean
  enabled: boolean
}

function MenuEditor({
  open,
  mode,
  node,
  parentId,
  nodes,
  onClose,
  onSaved,
}: {
  open: boolean
  mode: 'create' | 'edit'
  node: MenuNode | null
  parentId: string | null
  nodes: MenuNode[]
  onClose: () => void
  onSaved: () => void
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<MenuFormValues>()
  const [saving, setSaving] = useState(false)
  const type = Form.useWatch('type', form) ?? (mode === 'edit' ? node?.type : initialType(nodes, parentId))
  const selfId = mode === 'edit' ? node?.id ?? null : null

  async function save() {
    const values = await form.validateFields()
    const parentKey = values.parentKey
    const nextParent = !parentKey || parentKey === ROOT_PARENT ? null : parentKey
    const draft: MenuWrite = cleanMenuWrite({
      parentId: nextParent,
      type: values.type,
      name: values.name,
      permCode: values.permCode ?? null,
      path: values.path ?? null,
      component: values.component ?? null,
      icon: values.icon ?? null,
      sortNo: values.sortNo ?? 0,
      visible: values.visible,
      enabled: values.enabled,
      lockVersion: mode === 'edit' ? node?.lockVersion : undefined,
    })
    const problems = [...menuProblems(draft), ...parentProblems(nodes, draft.parentId, draft.type, selfId)]
    if (problems.length > 0) {
      message.error(problems.join('；'))
      return
    }
    setSaving(true)
    try {
      if (mode === 'create') await createMenu(draft)
      else if (node) await updateMenu(node.id, draft)
      message.success(mode === 'create' ? '已创建' : '已保存')
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
      title={mode === 'create' ? '新增菜单' : '编辑菜单'}
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
        initialValues={initialValues(mode, node, parentId, nodes)}
        onValuesChange={(changed) => {
          if (typeof changed.type !== 'string') return
          const parentKey = form.getFieldValue('parentKey') as string | undefined
          const nextParent = !parentKey || parentKey === ROOT_PARENT ? null : parentKey
          if (parentProblems(nodes, nextParent, changed.type, selfId).length > 0) {
            form.setFieldValue('parentKey', changed.type === 'BUTTON' ? undefined : ROOT_PARENT)
          }
        }}
      >
        <Form.Item label="类型" name="type" rules={[{ required: true, message: '请选择类型' }]}>
          <Select aria-label="类型" virtual={false} options={MENU_TYPES.map((item) => ({ value: item.value, label: item.label }))} />
        </Form.Item>
        <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item label="父节点" name="parentKey">
          <TreeSelect
            aria-label="父节点"
            allowClear={type === 'BUTTON'}
            treeDefaultExpandAll
            virtual={false}
            treeData={parentChoices(nodes, type ?? 'MENU', selfId)}
            treeNodeFilterProp="title"
            showSearch
          />
        </Form.Item>
        {type === 'DIR' ? null : (
          <Form.Item label="权限点" name="permCode">
            <Input autoComplete="off" />
          </Form.Item>
        )}
        {type === 'MENU' ? (
          <>
            <Form.Item label="路由" name="path">
              <Input autoComplete="off" />
            </Form.Item>
            <Form.Item label="组件" name="component">
              <Input autoComplete="off" />
            </Form.Item>
          </>
        ) : null}
        <Form.Item label="图标" name="icon">
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item label="排序" name="sortNo">
          <InputNumber min={0} precision={0} style={{ width: 160 }} />
        </Form.Item>
        <Form.Item label="显示" name="visible" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item label="启用" name="enabled" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Drawer>
  )
}

function MoveMenu({
  node,
  nodes,
  onClose,
  onMoved,
}: {
  node: MenuNode
  nodes: MenuNode[]
  onClose: () => void
  onMoved: () => void
}) {
  const { message } = App.useApp()
  const [parentKey, setParentKey] = useState(node.parentId ?? ROOT_PARENT)
  const [saving, setSaving] = useState(false)

  async function save() {
    const parentId = !parentKey || parentKey === ROOT_PARENT ? null : parentKey
    const problems = parentProblems(nodes, parentId, node.type, node.id)
    if (problems.length > 0) {
      message.error(problems.join('；'))
      return
    }
    setSaving(true)
    try {
      await updateMenu(node.id, toWrite(node, { parentId }))
      message.success('已移动')
      onMoved()
    } catch (err) {
      if (!isStale(err) && !isAuthRedirect(err)) message.error(formatProblem(toApiError(err).problem))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open
      title="移动菜单"
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => { void save() }}
    >
      <div className="mes-group-title">{node.name}</div>
      <TreeSelect
        aria-label="父节点"
        style={{ width: '100%' }}
        treeDefaultExpandAll
        virtual={false}
        value={parentKey}
        treeData={parentChoices(nodes, node.type, node.id)}
        treeNodeFilterProp="title"
        showSearch
        onChange={(value) => setParentKey(value ?? ROOT_PARENT)}
      />
    </Modal>
  )
}

function initialType(nodes: MenuNode[], parentId: string | null): string {
  if (!parentId) return 'MENU'
  const parent = findMenu(nodes, parentId)
  return parent ? suggestChildType(parent.type) : 'MENU'
}

function initialValues(
  mode: 'create' | 'edit',
  node: MenuNode | null,
  parentId: string | null,
  nodes: MenuNode[],
): MenuFormValues {
  if (mode === 'edit' && node) {
    return {
      type: node.type,
      name: node.name,
      parentKey: node.parentId ?? ROOT_PARENT,
      permCode: node.permCode ?? '',
      path: node.path ?? '',
      component: node.component ?? '',
      icon: node.icon ?? '',
      sortNo: node.sortNo,
      visible: node.visible,
      enabled: node.enabled,
    }
  }
  const type = initialType(nodes, parentId)
  return {
    type,
    name: '',
    parentKey: type === 'BUTTON' ? (parentId ?? undefined) : (parentId ?? ROOT_PARENT),
    permCode: '',
    path: '',
    component: '',
    icon: '',
    sortNo: 0,
    visible: true,
    enabled: true,
  }
}
