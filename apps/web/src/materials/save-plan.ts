import type { FieldDef, SchemaValues } from '../schema/types.ts'
import type {
  MaterialCreateBody,
  MaterialScope,
  MaterialStatus,
  MaterialUpdateBody,
  PlantExtPutBody,
} from '../api/types.ts'

export type HeaderInput = {
  scope: MaterialScope
  code: string
  name: string
  spec?: string
  materialType: string
  uomId: string
  drawingNo?: string
  remark?: string
}

function blankToNull(value: string | undefined): string | null {
  if (value == null) return null
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

export function toCreateBody(input: HeaderInput): MaterialCreateBody {
  return {
    scope: input.scope,
    code: input.code.trim(),
    name: input.name.trim(),
    spec: blankToNull(input.spec),
    materialType: input.materialType,
    uomId: input.uomId,
    drawingNo: blankToNull(input.drawingNo),
    remark: blankToNull(input.remark),
  }
}

export function toUpdateBody(
  input: HeaderInput,
  categoryId: string | null,
  lockVersion: number,
): MaterialUpdateBody {
  return {
    name: input.name.trim(),
    spec: blankToNull(input.spec),
    materialType: input.materialType,
    categoryId,
    uomId: input.uomId,
    drawingNo: blankToNull(input.drawingNo),
    remark: blankToNull(input.remark),
    lockVersion,
  }
}

/** Booleans are always sent, including `false`. Empty optional values are omitted (cleared). */
export function extPayload(fields: FieldDef[], values: SchemaValues): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const field of fields) {
    const value = values[field.key]
    if (field.type === 'boolean') {
      out[field.key] = value === true
      continue
    }
    if (value == null || value === '') continue
    if (Array.isArray(value) && value.length === 0) continue
    out[field.key] = value
  }
  return out
}

export function missingRequiredFiles(fields: FieldDef[], values: SchemaValues): FieldDef[] {
  return fields.filter((field) => {
    if (field.type !== 'image' && field.type !== 'file') return false
    if (!field.required) return false
    const value = values[field.key]
    return typeof value !== 'string' || value.length === 0
  })
}

export type SavePlan =
  | { kind: 'blocked'; reason: string }
  | { kind: 'create'; header: MaterialCreateBody; ext: PlantExtPutBody | null }
  | { kind: 'update'; header: MaterialUpdateBody | null; ext: PlantExtPutBody | null }

export function planMaterialSave(input: {
  mode: 'create' | 'edit'
  status?: MaterialStatus
  header: HeaderInput
  categoryId: string | null
  lockVersion: number
  fields: FieldDef[]
  extValues: SchemaValues
  extEnabled: boolean
  extLockVersion: number
}): SavePlan {
  const missing = missingRequiredFiles(input.fields, input.extValues)
  if (missing.length > 0) {
    const titles = missing.map((field) => field.title).join('、')
    return {
      kind: 'blocked',
      reason: `无法保存：${titles}即将上线。未提交物料。`,
    }
  }
  const values = extPayload(input.fields, input.extValues)
  const ext: PlantExtPutBody | null = input.fields.length
    ? {
        enabled: input.extEnabled,
        values,
        lockVersion: input.mode === 'create' ? 0 : input.extLockVersion,
      }
    : null

  if (input.mode === 'create') {
    return { kind: 'create', header: toCreateBody(input.header), ext }
  }
  if (input.status === 'OBSOLETE') {
    return { kind: 'blocked', reason: '物料已作废，主数据和本厂扩展都只读。' }
  }
  const header = input.status === 'DRAFT'
    ? toUpdateBody(input.header, input.categoryId, input.lockVersion)
    : null
  if (!header && !ext) {
    return { kind: 'blocked', reason: '没有可保存的内容。' }
  }
  return { kind: 'update', header, ext }
}
