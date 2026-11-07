import { useMemo } from 'react'
import {
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Switch,
  Tooltip,
  Typography,
  Upload,
  type FormInstance,
  type UploadFile,
  type UploadProps,
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { buildRules } from './rules.ts'
import type { FieldDef, FormSchema, SchemaValues } from './types.ts'

export type SchemaFileMode = 'unsupported' | 'mock'

export type SchemaFormProps = {
  schema: FormSchema
  initialValues?: SchemaValues
  onFinish?: (values: SchemaValues) => void
  /** `null` hides the built-in submit button so a parent drawer can submit. */
  submitText?: string | null
  form?: FormInstance
  disabled?: boolean
  /**
   * `unsupported` (default) does not invent storage keys.
   * `mock` keeps the skeleton demo upload and must not be used against the API.
   */
  fileMode?: SchemaFileMode
}

type FieldGroup = {
  name: string | null
  order: number
  fields: FieldDef[]
}

function clampSpan(span: number | undefined): number {
  if (span == null || Number.isNaN(span)) return 24
  return Math.min(24, Math.max(1, Math.floor(span)))
}

function fieldSortKey(field: FieldDef, index: number): number {
  return field.layout?.order ?? index
}

function groupFields(fields: FieldDef[]): FieldGroup[] {
  const indexed = fields.map((field, index) => ({ field, index }))
  indexed.sort((a, b) => fieldSortKey(a.field, a.index) - fieldSortKey(b.field, b.index))

  const groups = new Map<string, FieldGroup>()
  const order: string[] = []

  for (const { field, index } of indexed) {
    const name = field.layout?.group ?? null
    const mapKey = name ?? `__ungrouped_${field.key}`
    const sort = fieldSortKey(field, index)
    const existing = groups.get(mapKey)
    if (existing) {
      existing.fields.push(field)
      existing.order = Math.min(existing.order, sort)
    } else {
      groups.set(mapKey, { name, order: sort, fields: [field] })
      order.push(mapKey)
    }
  }

  return order.map((key) => groups.get(key)!).sort((a, b) => a.order - b.order)
}

function mockStorageKey(file: File, kind: 'image' | 'file'): string {
  const safeName = file.name.replace(/[^\w.-]+/g, '_')
  return `mock/${kind}/${Date.now()}-${safeName}`
}

type InjectedControlProps = {
  id?: string
  value?: unknown
  checked?: boolean
  onChange?: (value: unknown) => void
}

function StorageUpload({
  value,
  onChange,
  kind,
  disabled,
  id,
}: {
  value?: string
  onChange?: (key: string | undefined) => void
  kind: 'image' | 'file'
  disabled?: boolean
  id?: string
}) {
  const fileList: UploadFile[] = value
    ? [{ uid: value, name: value.split('/').pop() ?? value, status: 'done' }]
    : []

  const props: UploadProps = {
    id,
    maxCount: 1,
    listType: kind === 'image' ? 'picture-card' : 'text',
    accept: kind === 'image' ? 'image/*' : undefined,
    fileList,
    disabled,
    beforeUpload: (file) => {
      onChange?.(mockStorageKey(file, kind))
      return false
    },
    onRemove: () => {
      onChange?.(undefined)
    },
  }

  return (
    <Upload {...props}>
      {fileList.length === 0
        ? kind === 'image'
          ? '上传图片'
          : <Button type="default" disabled={disabled}>选择文件</Button>
        : null}
    </Upload>
  )
}

function UnsupportedFile({
  value,
  id,
  kind,
}: {
  value?: string
  id?: string
  kind: 'image' | 'file'
}) {
  const noun = kind === 'image' ? '图片' : '文件'
  return (
    <Tooltip title="即将上线">
      <span className="mes-soon">
        <Input
          id={id}
          readOnly
          className="mes-soon-input"
          value={value ?? ''}
          placeholder={`${noun}即将上线`}
        />
      </span>
    </Tooltip>
  )
}

function FieldControl({
  field,
  disabled,
  fileMode,
  id,
  value,
  checked,
  onChange,
}: {
  field: FieldDef
  disabled?: boolean
  fileMode: SchemaFileMode
} & InjectedControlProps) {
  switch (field.type) {
    case 'string':
      return (
        <Input
          id={id}
          allowClear
          disabled={disabled}
          value={typeof value === 'string' ? value : ''}
          onChange={onChange}
          placeholder={`请输入${field.title}`}
        />
      )
    case 'number':
      return (
        <InputNumber
          id={id}
          disabled={disabled}
          style={{ width: '100%' }}
          value={typeof value === 'number' ? value : undefined}
          onChange={onChange}
          placeholder={`请输入${field.title}`}
        />
      )
    case 'datetime':
      return (
        <DatePicker
          id={id}
          disabled={disabled}
          showTime
          style={{ width: '100%' }}
          value={dayjs.isDayjs(value) ? value : undefined}
          onChange={onChange}
          placeholder={`请选择${field.title}`}
        />
      )
    case 'enum':
      return (
        <Select
          id={id}
          allowClear
          disabled={disabled}
          virtual={false}
          options={field.options ?? []}
          value={typeof value === 'string' ? value : undefined}
          onChange={onChange}
          placeholder={`请选择${field.title}`}
        />
      )
    case 'multi_enum':
      return (
        <Select
          id={id}
          mode="multiple"
          allowClear
          disabled={disabled}
          virtual={false}
          options={field.options ?? []}
          value={Array.isArray(value) ? value : []}
          onChange={onChange}
          placeholder={`请选择${field.title}`}
        />
      )
    case 'boolean':
      return <Switch id={id} disabled={disabled} checked={checked} onChange={onChange} />
    case 'image':
    case 'file':
      return fileMode === 'mock' ? (
        <StorageUpload
          id={id}
          kind={field.type}
          disabled={disabled}
          value={typeof value === 'string' ? value : undefined}
          onChange={onChange}
        />
      ) : (
        <UnsupportedFile
          id={id}
          kind={field.type}
          value={typeof value === 'string' ? value : undefined}
        />
      )
    default: {
      const unknownType = (field as FieldDef & { type: string }).type
      if (import.meta.env.DEV) {
        console.warn(`[SchemaForm] unsupported field type: ${unknownType}`, field)
      }
      return (
        <Typography.Text type="warning">
          {`不支持的字段类型：${unknownType}`}
        </Typography.Text>
      )
    }
  }
}

/** Required booleans start at `false` so an untouched Switch counts as filled. */
export function toFormInitialValues(
  fields: FieldDef[],
  values: SchemaValues | undefined,
): Record<string, unknown> {
  const next: Record<string, unknown> = {}
  for (const field of fields) {
    const raw = values?.[field.key]
    if (field.type === 'boolean') {
      next[field.key] = typeof raw === 'boolean' ? raw : false
      continue
    }
    if (raw == null) continue
    if (field.type === 'datetime' && typeof raw === 'string') {
      const parsed = dayjs(raw)
      next[field.key] = parsed.isValid() ? parsed : undefined
    } else {
      next[field.key] = raw
    }
  }
  return next
}

export function fromFormValues(
  fields: FieldDef[],
  values: Record<string, unknown>,
): SchemaValues {
  const next: SchemaValues = {}
  for (const field of fields) {
    const raw = values[field.key]
    if (field.type === 'boolean') {
      next[field.key] = raw === true
      continue
    }
    if (raw == null || raw === '') continue
    if (field.type === 'datetime' && dayjs.isDayjs(raw)) {
      next[field.key] = (raw as Dayjs).toISOString()
      continue
    }
    next[field.key] = raw as SchemaValues[string]
  }
  return next
}

/**
 * Thin schema → Ant Design Form renderer.
 * Maps FieldDef.type to controls; ignores unknown schema keys.
 */
export function SchemaForm({
  schema,
  initialValues,
  onFinish,
  submitText = '提交',
  form,
  disabled = false,
  fileMode = 'unsupported',
}: SchemaFormProps) {
  const [ownedForm] = Form.useForm()
  const formInstance = form ?? ownedForm
  const fields = Array.isArray(schema?.fields) ? schema.fields : []
  const groups = useMemo(() => groupFields(fields), [fields])
  const formInitial = useMemo(
    () => toFormInitialValues(fields, initialValues),
    [fields, initialValues],
  )

  return (
    <Form
      form={formInstance}
      layout="vertical"
      disabled={disabled}
      initialValues={formInitial}
      data-testid="schema-form"
      onFinish={(vals) => {
        onFinish?.(fromFormValues(fields, vals as Record<string, unknown>))
      }}
    >
      {groups.map((group) => (
        <div
          key={group.name ?? group.fields.map((field) => field.key).join(',')}
          style={{ marginBottom: 8 }}
        >
          {group.name ? (
            <div className="mes-group-title">
              {group.name}
            </div>
          ) : null}
          <Row gutter={16}>
            {group.fields.map((field) => (
              <Col key={field.key} span={clampSpan(field.layout?.span)}>
                <Form.Item
                  name={field.key}
                  label={field.title}
                  rules={buildRules(field)}
                  valuePropName={field.type === 'boolean' ? 'checked' : 'value'}
                >
                  <FieldControl field={field} disabled={disabled} fileMode={fileMode} />
                </Form.Item>
              </Col>
            ))}
          </Row>
        </div>
      ))}
      {submitText != null ? (
        <Form.Item>
          <Button type="primary" htmlType="submit" disabled={disabled}>
            {submitText}
          </Button>
        </Form.Item>
      ) : null}
    </Form>
  )
}

export default SchemaForm
