/** Closed field type set for the V1 schema renderer. */
export type FieldType =
  | 'string'
  | 'number'
  | 'datetime'
  | 'enum'
  | 'multi_enum'
  | 'boolean'
  | 'image'
  | 'file'

export type FieldOption = {
  value: string
  label: string
}

/** JSON Schema-aligned constraints used by the renderer. */
export type FieldValidation = {
  minLength?: number
  maxLength?: number
  pattern?: string
  min?: number
  max?: number
  minItems?: number
  maxItems?: number
}

/** Layout hints only; ignored by domain storage. */
export type FieldLayout = {
  order?: number
  group?: string
  /** Column span hint in 1–24. */
  span?: number
}

export type FieldDef = {
  key: string
  type: FieldType
  title: string
  required?: boolean
  options?: FieldOption[]
  validation?: FieldValidation
  layout?: FieldLayout
}

export type FormSchema = {
  /** Defaults to 1 when omitted. */
  schemaVersion?: number
  fields: FieldDef[]
}

/** Value shapes bound to each FieldType. */
export type SchemaValue =
  | string
  | number
  | boolean
  | string[]
  | null
  | undefined

export type SchemaValues = Record<string, SchemaValue>
