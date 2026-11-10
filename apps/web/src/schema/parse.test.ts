import { describe, expect, it } from 'vitest'
import { parseFormSchema } from './parse.ts'

describe('parseFormSchema', () => {
  it('drops unknown keys and keeps an unknown type for the renderer', () => {
    const schema = parseFormSchema({
      schemaVersion: 1,
      fields: [{
        key: 'surface_finish',
        type: 'enum',
        title: '表面处理',
        widget: 'Input',
        options: [{ value: 'anodize', label: '阳极氧化' }],
      }, {
        key: 'odd',
        type: 'x-component',
        title: '未知',
      }],
    })
    expect(schema.fields[0]).not.toHaveProperty('widget')
    expect(schema.fields[0]?.options?.[0]?.label).toBe('阳极氧化')
    expect(schema.fields[1]?.type).toBe('x-component')
  })

  it('returns an empty field list for a missing document', () => {
    expect(parseFormSchema(null).fields).toEqual([])
  })
})
