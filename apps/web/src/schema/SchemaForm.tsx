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
  Upload,
  type UploadFile,
  type UploadProps,
} from 'antd'
import type { Rule } from 'antd/es/form'
import dayjs, { type Dayjs } from 'dayjs'
import type {
  FieldDef,
  FieldValidation,
  FormSchema,
  SchemaValues,
} from './types.ts'

export type SchemaFormProps = {
  schema: FormSchema
  initialValues?: SchemaValues
  onFinish?: (values: SchemaValues) => void
  submitText?: string
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

/** Group fields: same group together; group order = min order in group. */
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

  return order
    .map((key) => groups.get(key)!)
    .sort((a, b) => a.order - b.order)
}

function buildRules(field: FieldDef): Rule[] {
  const rules: Rule[] = []
  if (field.required) {
    rules.push({
      required: true,
      message: `请填写${field.title}`,
    })
  }

  const v: FieldValidation | undefined = field.validation
  if (!v) return rules

  if (field.type === 'string') {
    if (v.minLength != null) {
      rules.push({
        min: v.minLength,
        message: `${field.title}至少 ${v.minLength} 个字符`,
      })
    }
    if (v.maxLength != null) {
      rules.push({
        max: v.maxLength,
        message: `${field.title}最多 ${v.maxLength} 个字符`,
      })
    }
    if (v.pattern) {
      rules.push({
        pattern: new RegExp(v.pattern),
        message: `${field.title}格式不正确`,
      })
    }
  }

  if (field.type === 'number') {
    if (v.min != null) {
      rules.push({
        type: 'number',
        min: v.min,
        message: `${field.title}不能小于 ${v.min}`,
      })
    }
    if (v.max != null) {
      rules.push({
        type: 'number',
        max: v.max,
        message: `${field.title}不能大于 ${v.max}`,
      })
    }
  }

  if (field.type === 'multi_enum') {
    if (v.minItems != null) {
      rules.push({
        type: 'array',
        min: v.minItems,
        message: `${field.title}至少选择 ${v.minItems} 项`,
      })
    }
    if (v.maxItems != null) {
      rules.push({
        type: 'array',
        max: v.maxItems,
        message: `${field.title}最多选择 ${v.maxItems} 项`,
      })
    }
  }

  return rules
}

function mockStorageKey(file: File, kind: 'image' | 'file'): string {
  const safeName = file.name.replace(/[^\w.-]+/g, '_')
  return `mock/${kind}/${Date.now()}-${safeName}`
}

function StorageUpload({
  value,
  onChange,
  kind,
}: {
  value?: string
  onChange?: (key: string | undefined) => void
  kind: 'image' | 'file'
}) {
  const fileList: UploadFile[] = value
    ? [
        {
          uid: value,
          name: value.split('/').pop() ?? value,
          status: 'done',
        },
      ]
    : []

  const props: UploadProps = {
    maxCount: 1,
    listType: kind === 'image' ? 'picture-card' : 'text',
    accept: kind === 'image' ? 'image/*' : undefined,
    fileList,
    beforeUpload: (file) => {
      const key = mockStorageKey(file, kind)
      onChange?.(key)
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
          : <Button type="default">选择文件</Button>
        : null}
    </Upload>
  )
}

function FieldControl({ field }: { field: FieldDef }) {
  switch (field.type) {
    case 'string':
      return <Input allowClear placeholder={`请输入${field.title}`} />
    case 'number':
      return (
        <InputNumber
          style={{ width: '100%' }}
          placeholder={`请输入${field.title}`}
        />
      )
    case 'datetime':
      return (
        <DatePicker
          showTime
          style={{ width: '100%' }}
          placeholder={`请选择${field.title}`}
        />
      )
    case 'enum':
      return (
        <Select
          allowClear
          options={field.options}
          placeholder={`请选择${field.title}`}
        />
      )
    case 'multi_enum':
      return (
        <Select
          mode="multiple"
          allowClear
          options={field.options}
          placeholder={`请选择${field.title}`}
        />
      )
    case 'boolean':
      return <Switch />
    case 'image':
      return <StorageUpload kind="image" />
    case 'file':
      return <StorageUpload kind="file" />
    default:
      return null
  }
}

function toFormInitialValues(
  fields: FieldDef[],
  values: SchemaValues | undefined,
): Record<string, unknown> {
  if (!values) return {}
  const next: Record<string, unknown> = {}
  for (const field of fields) {
    const raw = values[field.key]
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

function fromFormValues(
  fields: FieldDef[],
  values: Record<string, unknown>,
): SchemaValues {
  const next: SchemaValues = {}
  for (const field of fields) {
    const raw = values[field.key]
    if (raw == null) {
      next[field.key] = undefined
      continue
    }
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
}: SchemaFormProps) {
  const [form] = Form.useForm()
  const fields = schema.fields
  const groups = useMemo(() => groupFields(fields), [fields])
  const formInitial = useMemo(
    () => toFormInitialValues(fields, initialValues),
    [fields, initialValues],
  )

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={formInitial}
      onFinish={(vals) => {
        onFinish?.(fromFormValues(fields, vals as Record<string, unknown>))
      }}
    >
      {groups.map((group) => (
        <div
          key={group.name ?? group.fields.map((f) => f.key).join(',')}
          style={{ marginBottom: 24 }}
        >
          {group.name ? (
            <div
              style={{
                fontWeight: 600,
                fontSize: 15,
                marginBottom: 12,
                color: 'rgba(0,0,0,0.88)',
              }}
            >
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
                  <FieldControl field={field} />
                </Form.Item>
              </Col>
            ))}
          </Row>
        </div>
      ))}
      <Form.Item>
        <Button type="primary" htmlType="submit">
          {submitText}
        </Button>
      </Form.Item>
    </Form>
  )
}

export default SchemaForm
