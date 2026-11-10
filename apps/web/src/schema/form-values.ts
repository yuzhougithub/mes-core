import dayjs, { type Dayjs } from 'dayjs'
import type { FieldDef, SchemaValues } from './types.ts'

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
