import type { FieldDef, FieldLayout, FieldOption, FieldValidation, FormSchema } from './types.ts'

const KNOWN_TYPES = new Set([
  'string',
  'number',
  'datetime',
  'enum',
  'multi_enum',
  'boolean',
  'image',
  'file',
])

function parseOptions(raw: unknown): FieldOption[] | undefined {
  if (!Array.isArray(raw)) return undefined
  const options: FieldOption[] = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const record = item as Record<string, unknown>
    if (typeof record.value !== 'string' || typeof record.label !== 'string') continue
    options.push({ value: record.value, label: record.label })
  }
  return options
}

function parseValidation(raw: unknown): FieldValidation | undefined {
  if (!raw || typeof raw !== 'object') return undefined
  const record = raw as Record<string, unknown>
  const validation: FieldValidation = {}
  if (typeof record.minLength === 'number') validation.minLength = record.minLength
  if (typeof record.maxLength === 'number') validation.maxLength = record.maxLength
  if (typeof record.pattern === 'string') validation.pattern = record.pattern
  if (typeof record.min === 'number') validation.min = record.min
  if (typeof record.max === 'number') validation.max = record.max
  if (typeof record.minItems === 'number') validation.minItems = record.minItems
  if (typeof record.maxItems === 'number') validation.maxItems = record.maxItems
  return validation
}

function parseLayout(raw: unknown): FieldLayout | undefined {
  if (!raw || typeof raw !== 'object') return undefined
  const record = raw as Record<string, unknown>
  const layout: FieldLayout = {}
  if (typeof record.order === 'number') layout.order = record.order
  if (typeof record.group === 'string') layout.group = record.group
  if (typeof record.span === 'number') layout.span = record.span
  return layout
}

/**
 * Read-side parser. Unknown keys (`widget`, `x-component`, …) are dropped.
 * Unknown `type` strings are kept so the renderer can show a warning instead of crashing.
 */
export function parseFormSchema(raw: unknown): FormSchema {
  const record = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {}
  const schemaVersion = typeof record.schemaVersion === 'number' ? record.schemaVersion : 1
  const source = Array.isArray(record.fields) ? record.fields : []
  const fields: FieldDef[] = []
  for (const item of source) {
    if (!item || typeof item !== 'object') continue
    const field = item as Record<string, unknown>
    if (typeof field.key !== 'string' || typeof field.title !== 'string') continue
    const type = typeof field.type === 'string' ? field.type : 'unknown'
    fields.push({
      key: field.key,
      type: (KNOWN_TYPES.has(type) ? type : type) as FieldDef['type'],
      title: field.title,
      required: field.required === true,
      options: parseOptions(field.options),
      validation: parseValidation(field.validation),
      layout: parseLayout(field.layout),
    })
  }
  return { schemaVersion, fields }
}
