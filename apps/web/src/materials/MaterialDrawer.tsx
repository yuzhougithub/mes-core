import { Alert, App, Button, Col, Drawer, Form, Input, Radio, Row, Select, Space, Tooltip, Typography } from 'antd'
import { useEffect, useState } from 'react'
import {
  createMaterial,
  getMaterial,
  getMaterialSchema,
  getUom,
  listUoms,
  putPlantExt,
  updateMaterial,
} from '../api/resources.ts'
import { ApiError, formatProblem, isAuthRedirect, isStale, toApiError, type ProblemDetails } from '../api/problem.ts'
import { getPlantEpoch } from '../api/plant-session.ts'
import type { Material, MaterialStatus, Uom } from '../api/types.ts'
import { fromFormValues } from '../schema/form-values.ts'
import { SchemaForm } from '../schema/SchemaForm.tsx'
import { parseFormSchema } from '../schema/parse.ts'
import type { FieldDef, FormSchema, SchemaValues } from '../schema/types.ts'
import { MATERIAL_TYPES } from './constants.ts'
import { planMaterialSave, type HeaderInput } from './save-plan.ts'

export type DrawerMode = 'create' | 'edit' | 'detail'

type Props = {
  open: boolean
  mode: DrawerMode
  materialId?: string
  onClose: () => void
  onSaved: () => void
  onPartialCreate: (id: string) => void
}

function schemaValuesOf(raw: Record<string, unknown> | undefined): SchemaValues {
  const values: SchemaValues = {}
  if (!raw) return values
  for (const [key, value] of Object.entries(raw)) {
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      values[key] = value
    } else if (Array.isArray(value) && value.every((item) => typeof item === 'string')) {
      values[key] = value
    }
  }
  return values
}

export function MaterialDrawer({
  open,
  mode,
  materialId,
  onClose,
  onSaved,
  onPartialCreate,
}: Props) {
  const { message } = App.useApp()
  const [headerForm] = Form.useForm<HeaderInput>()
  const [extForm] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [saveError, setSaveError] = useState<ProblemDetails | null>(null)
  const [detail, setDetail] = useState<Material | null>(null)
  const [schema, setSchema] = useState<FormSchema>({ schemaVersion: 1, fields: [] })
  const [uoms, setUoms] = useState<Uom[]>([])
  const [attempt, setAttempt] = useState(0)
  const loadScope = `${mode}:${materialId ?? ''}`
  const [seenScope, setSeenScope] = useState(loadScope)
  if (seenScope !== loadScope) {
    setSeenScope(loadScope)
    setLoading(true)
    setLoadError(null)
    setSaveError(null)
    setDetail(null)
  }

  function beginLoad() {
    setLoading(true)
    setLoadError(null)
    setSaveError(null)
    setDetail(null)
  }

  useEffect(() => {
    if (!open) return
    const epoch = getPlantEpoch()
    let cancelled = false
    const materialPromise = mode === 'create' || !materialId
      ? Promise.resolve(null)
      : getMaterial(materialId)
    Promise.all([materialPromise, getMaterialSchema(), listUoms({ enabled: true, size: 200 })])
      .then(async ([material, schemaView, uomPage]) => {
        if (cancelled || getPlantEpoch() !== epoch) return
        let items = uomPage.items
        if (material && !items.some((uom) => uom.id === material.uomId)) {
          try {
            items = [await getUom(material.uomId), ...items]
          } catch (err) {
            if (isStale(err) || isAuthRedirect(err) || cancelled || getPlantEpoch() !== epoch) return
          }
        }
        if (cancelled || getPlantEpoch() !== epoch) return
        setDetail(material)
        setSchema(parseFormSchema(schemaView.schema))
        setUoms(items)
        setLoadError(null)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (cancelled || isStale(err) || isAuthRedirect(err) || getPlantEpoch() !== epoch) return
        setLoadError(toApiError(err))
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, mode, materialId, attempt])

  const status: MaterialStatus | undefined = detail?.status
  const headerLocked = mode === 'detail' || (mode === 'edit' && status !== 'DRAFT')
  const extLocked = mode === 'detail' || status === 'OBSOLETE'
  const fields: FieldDef[] = schema.fields
  const typeOptions = MATERIAL_TYPES.map((item) => ({ value: item.value, label: `${item.label}（${item.value}）` }))
  if (detail && !MATERIAL_TYPES.some((item) => item.value === detail.materialType)) {
    typeOptions.push({ value: detail.materialType, label: detail.materialType })
  }

  function applyProblem(problem: ProblemDetails) {
    setSaveError(problem)
    if (problem.code === 'DUPLICATE_CODE') {
      headerForm.setFields([{ name: 'code', errors: [problem.detail || '编码已存在'] }])
    }
    const headerNames = ['code', 'name', 'spec', 'materialType', 'uomId', 'drawingNo', 'remark', 'scope'] as const
    type HeaderName = (typeof headerNames)[number]
    const headerFields: { name: HeaderName; errors: string[] }[] = []
    const extFields: { name: string; errors: string[] }[] = []
    for (const item of problem.errors) {
      if (!item.field) continue
      if (item.field.startsWith('values.')) {
        extFields.push({ name: item.field.slice('values.'.length), errors: [item.message] })
      } else if (headerNames.includes(item.field as HeaderName)) {
        headerFields.push({ name: item.field as HeaderName, errors: [item.message] })
      }
    }
    if (headerFields.length) headerForm.setFields(headerFields)
    if (extFields.length) extForm.setFields(extFields)
  }

  async function save() {
    setSaveError(null)
    let header: HeaderInput
    try {
      header = await headerForm.validateFields()
    } catch {
      return
    }
    let extValues: SchemaValues = {}
    if (fields.length > 0 && !extLocked) {
      try {
        const raw = await extForm.validateFields()
        extValues = fromFormValues(fields, raw as Record<string, unknown>)
      } catch {
        return
      }
    }
    const plan = planMaterialSave({
      mode: mode === 'create' ? 'create' : 'edit',
      status,
      header,
      categoryId: detail?.categoryId ?? null,
      lockVersion: detail?.lockVersion ?? 0,
      fields: extLocked ? [] : fields,
      extValues,
      extEnabled: detail?.plantExt?.enabled ?? true,
      extLockVersion: detail?.plantExt?.lockVersion ?? 0,
    })
    if (plan.kind === 'blocked') {
      message.error(plan.reason)
      return
    }
    setSaving(true)
    try {
      if (plan.kind === 'create') {
        const created = await createMaterial(plan.header)
        if (plan.ext) {
          try {
            await putPlantExt(created.id, plan.ext)
          } catch (err) {
            if (isStale(err) || isAuthRedirect(err)) return
            const problem = toApiError(err).problem
            message.error(`物料已创建，但补充信息没有保存。${problem.detail || problem.title}`)
            onPartialCreate(created.id)
            return
          }
        }
        message.success('已创建')
        onSaved()
        return
      }
      if (!materialId) return
      if (plan.header) await updateMaterial(materialId, plan.header)
      if (plan.ext) {
        try {
          await putPlantExt(materialId, plan.ext)
        } catch (err) {
          if (isStale(err) || isAuthRedirect(err)) return
          applyProblem(toApiError(err).problem)
          message.error('物料已保存，扩展信息没有保存成功。请再试一次。')
          beginLoad()
          setAttempt((value) => value + 1)
          return
        }
      }
      message.success('已保存')
      onSaved()
    } catch (err) {
      if (isStale(err) || isAuthRedirect(err)) return
      const problem = toApiError(err).problem
      applyProblem(problem)
      if (problem.code === 'STALE_VERSION') {
        message.warning('数据已变化，请按最新内容重新保存。')
        beginLoad()
        setAttempt((value) => value + 1)
      }
    } finally {
      setSaving(false)
    }
  }

  const title = mode === 'create' ? '新增物料' : mode === 'detail' ? '物料详情' : '编辑物料'
  const initialHeader: HeaderInput | undefined = detail
    ? {
        scope: detail.scope,
        code: detail.code,
        name: detail.name,
        spec: detail.spec ?? '',
        materialType: detail.materialType,
        uomId: detail.uomId,
        drawingNo: detail.drawingNo ?? '',
        remark: detail.remark ?? '',
      }
    : {
        scope: 'PLANT',
        code: '',
        name: '',
        spec: '',
        materialType: 'SEMI',
        uomId: '',
        drawingNo: '',
        remark: '',
      }

  return (
    <Drawer
      title={title}
      width={720}
      open={open}
      onClose={onClose}
      destroyOnHidden
      extra={mode === 'detail' ? null : undefined}
      footer={
        mode === 'detail' ? (
          <Button onClick={onClose}>关闭</Button>
        ) : (
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" loading={saving} disabled={loading || !!loadError || status === 'OBSOLETE'} onClick={() => void save()}>
              保存
            </Button>
          </Space>
        )
      }
    >
      {loading ? <Typography.Text>正在加载物料、计量单位和自定义字段…</Typography.Text> : null}
      {loadError ? (
        <Alert
          type="error"
          showIcon
          message={loadError.problem.title}
          description={
            <div>
              <div style={{ whiteSpace: 'pre-wrap' }}>{formatProblem(loadError.problem)}</div>
              <Button
                size="small"
                style={{ marginTop: 8 }}
                onClick={() => {
                  beginLoad()
                  setAttempt((value) => value + 1)
                }}
              >
                重试
              </Button>
            </div>
          }
        />
      ) : null}
      {!loading && !loadError ? (
        <>
          {mode === 'edit' && status === 'RELEASED' ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="已发布，基本资料不能修改。"
            />
          ) : null}
          {status === 'OBSOLETE' ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="已作废，不能修改。"
            />
          ) : null}
          {saveError ? (
            <Alert
              type="error"
              showIcon
              style={{ marginBottom: 16 }}
              message={`${saveError.title}（${saveError.code}）`}
              description={<span style={{ whiteSpace: 'pre-wrap' }}>{formatProblem(saveError)}</span>}
            />
          ) : null}
          <Form
            form={headerForm}
            layout="vertical"
            disabled={headerLocked}
            initialValues={initialHeader}
            requiredMark
          >
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="scope" label="范围" rules={[{ required: true, message: '请选择范围' }]}>
                  <Radio.Group disabled={mode !== 'create'}>
                    <Radio value="PLANT">本厂</Radio>
                    <Radio value="SHARED">共享</Radio>
                  </Radio.Group>
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="code"
                  label="编码"
                  rules={[{ required: true, message: '请填写编码' }, { max: 64, message: '编码最多 64 个字符' }]}
                  extra={mode === 'create' ? undefined : '创建后不可改。'}
                >
                  <Input disabled={mode !== 'create'} maxLength={64} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="name" label="名称" rules={[{ required: true, message: '请填写名称' }, { max: 200, message: '名称最多 200 个字符' }]}>
                  <Input maxLength={200} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="spec" label="规格" rules={[{ max: 500, message: '规格最多 500 个字符' }]}>
                  <Input maxLength={500} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="materialType"
                  label="物料类型"
                  rules={[{ required: true, message: '请选择物料类型' }]}
                >
                  <Select virtual={false} options={typeOptions} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="uomId"
                  label="单位"
                  rules={[{ required: true, message: '请选择单位' }]}
                >
                  <Select
                    virtual={false}
                    showSearch
                    optionFilterProp="label"
                    options={uoms.map((uom) => ({
                      value: uom.id,
                      label: `${uom.name}（${uom.code}）`,
                    }))}
                    placeholder={uoms.length ? '请选择单位' : '没有可用计量单位'}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="分类">
                  <Tooltip title="即将上线">
                    <span className="mes-soon">
                      <Input disabled value="" placeholder="—" />
                    </span>
                  </Tooltip>
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="drawingNo" label="图号" rules={[{ max: 64, message: '图号最多 64 个字符' }]}>
                  <Input maxLength={64} />
                </Form.Item>
              </Col>
              <Col span={24}>
                <Form.Item name="remark" label="备注" rules={[{ max: 500, message: '备注最多 500 个字符' }]}>
                  <Input.TextArea rows={2} maxLength={500} />
                </Form.Item>
              </Col>
            </Row>
          </Form>
          <div className="mes-group-title">本厂自定义字段</div>
          {detail?.plantExt && detail.plantExt.enabled === false ? (
            <span className="mes-status-tag is-stopped">
              <span className="mes-status-dot" />
              停用
            </span>
          ) : null}
          {fields.length === 0 ? (
            <Typography.Text type="secondary">当前工厂没有物料自定义字段。</Typography.Text>
          ) : (
            <SchemaForm
              key={`${materialId ?? 'new'}-${attempt}`}
              form={extForm}
              schema={schema}
              disabled={extLocked}
              submitText={null}
              fileMode="unsupported"
              initialValues={schemaValuesOf(detail?.plantExt?.values)}
            />
          )}
        </>
      ) : null}
    </Drawer>
  )
}
