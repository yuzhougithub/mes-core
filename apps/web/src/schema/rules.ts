import type { Rule } from 'antd/es/form'
import type { FieldDef, FieldValidation } from './types.ts'

/** Unicode code points, matching the API pattern length cap. */
export function patternCodePoints(pattern: string): number {
  return Array.from(pattern).length
}

/**
 * Compile a schema pattern for an immediate hint. Illegal or over-long patterns
 * are skipped so a bad definition cannot crash the form. The API remains authoritative.
 */
export function compilePattern(pattern: string | undefined, fieldKey: string): RegExp | null {
  if (!pattern) return null
  const points = patternCodePoints(pattern)
  if (points > 200) {
    console.warn(`[SchemaForm] skip pattern for field "${fieldKey}": length ${points} > 200`)
    return null
  }
  try {
    return new RegExp(pattern)
  } catch (err) {
    console.warn(`[SchemaForm] skip invalid pattern for field "${fieldKey}":`, pattern, err)
    return null
  }
}

export function buildRules(field: FieldDef): Rule[] {
  const rules: Rule[] = []
  if (field.required && field.type === 'boolean') {
    rules.push({
      validator: async (_rule, value) => {
        if (typeof value !== 'boolean') {
          throw new Error(`请填写${field.title}`)
        }
      },
    })
  } else if (field.required) {
    rules.push({
      required: true,
      message: `请填写${field.title}`,
    })
  }

  const validation: FieldValidation | undefined = field.validation
  if (!validation) return rules

  if (field.type === 'string') {
    if (validation.minLength != null) {
      rules.push({
        min: validation.minLength,
        message: `${field.title}至少 ${validation.minLength} 个字符`,
      })
    }
    if (validation.maxLength != null) {
      rules.push({
        max: validation.maxLength,
        message: `${field.title}最多 ${validation.maxLength} 个字符`,
      })
    }
    const pattern = compilePattern(validation.pattern, field.key)
    if (pattern) {
      rules.push({
        validator: async (_rule, value) => {
          if (value == null || value === '') return
          let matched = true
          try {
            matched = pattern.test(String(value))
          } catch (err) {
            console.warn(`[SchemaForm] pattern test failed for "${field.key}", skip:`, err)
            return
          }
          if (!matched) throw new Error(`${field.title}格式不正确`)
        },
      })
    }
  }

  if (field.type === 'number') {
    if (validation.min != null) {
      rules.push({
        type: 'number',
        min: validation.min,
        message: `${field.title}不能小于 ${validation.min}`,
      })
    }
    if (validation.max != null) {
      rules.push({
        type: 'number',
        max: validation.max,
        message: `${field.title}不能大于 ${validation.max}`,
      })
    }
  }

  if (field.type === 'multi_enum') {
    if (validation.minItems != null) {
      rules.push({
        type: 'array',
        min: validation.minItems,
        message: `${field.title}至少选择 ${validation.minItems} 项`,
      })
    }
    if (validation.maxItems != null) {
      rules.push({
        type: 'array',
        max: validation.maxItems,
        message: `${field.title}最多选择 ${validation.maxItems} 项`,
      })
    }
  }

  return rules
}
