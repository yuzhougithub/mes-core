import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SchemaForm } from './SchemaForm.tsx'
import type { FormSchema, SchemaValues } from './types.ts'
import { compilePattern, patternCodePoints } from './rules.ts'
import { toFormInitialValues } from './form-values.ts'

afterEach(() => {
  cleanup()
})

const booleanSchema: FormSchema = {
  schemaVersion: 1,
  fields: [{ key: 'rohs', type: 'boolean', title: 'RoHS', required: true }],
}

function Harness({
  schema,
  onFinish,
  fileMode,
}: {
  schema: FormSchema
  onFinish: (values: SchemaValues) => void
  fileMode?: 'unsupported' | 'mock'
}) {
  const [done, setDone] = useState<SchemaValues | null>(null)
  return (
    <ConfigProvider button={{ autoInsertSpace: false }}>
      <SchemaForm
        schema={schema}
        fileMode={fileMode}
        onFinish={(values) => {
          setDone(values)
          onFinish(values)
        }}
      />
      <output>{done ? JSON.stringify(done) : ''}</output>
    </ConfigProvider>
  )
}

describe('compilePattern', () => {
  it('skips illegal patterns instead of throwing', () => {
    expect(compilePattern('(', 'customer_pn')).toBeNull()
    expect(compilePattern('[', 'customer_pn')).toBeNull()
  })

  it('skips patterns longer than 200 code points', () => {
    const pattern = 'a'.repeat(201)
    expect(patternCodePoints(pattern)).toBe(201)
    expect(compilePattern(pattern, 'customer_pn')).toBeNull()
  })
})

describe('SchemaForm', () => {
  it('submits required boolean false when the switch stays off', async () => {
    const user = userEvent.setup()
    const onFinish = vi.fn()
    render(<Harness schema={booleanSchema} onFinish={onFinish} />)
    expect(toFormInitialValues(booleanSchema.fields, undefined).rohs).toBe(false)
    await user.click(screen.getByRole('button', { name: '提交' }))
    expect(onFinish).toHaveBeenCalledWith({ rohs: false })
  })

  it('keeps an explicit boolean true', async () => {
    const user = userEvent.setup()
    const onFinish = vi.fn()
    render(
      <ConfigProvider button={{ autoInsertSpace: false }}>
        <SchemaForm
          schema={booleanSchema}
          initialValues={{ rohs: true }}
          onFinish={onFinish}
        />
      </ConfigProvider>,
    )
    await user.click(screen.getByRole('button', { name: '提交' }))
    expect(onFinish).toHaveBeenCalledWith({ rohs: true })
  })

  it('renders and submits when a pattern is illegal', async () => {
    const user = userEvent.setup()
    const onFinish = vi.fn()
    const schema: FormSchema = {
      fields: [{
        key: 'customer_pn',
        type: 'string',
        title: '客户料号',
        validation: { pattern: '(' },
      }],
    }
    expect(() => render(<Harness schema={schema} onFinish={onFinish} />)).not.toThrow()
    await user.type(screen.getByRole('textbox'), 'ab')
    await user.click(screen.getByRole('button', { name: '提交' }))
    expect(onFinish).toHaveBeenCalledWith({ customer_pn: 'ab' })
  })

  it('shows unknown types instead of crashing', () => {
    const schema = {
      fields: [{ key: 'widget', type: 'x-component', title: '神秘字段' }],
    } as unknown as FormSchema
    render(<Harness schema={schema} onFinish={vi.fn()} />)
    expect(screen.getByText('不支持的字段类型：x-component')).toBeTruthy()
  })

  it('does not invent a storage key when file upload is unsupported', async () => {
    const user = userEvent.setup()
    const onFinish = vi.fn()
    const schema: FormSchema = {
      fields: [{ key: 'drawing', type: 'file', title: '图纸' }],
    }
    render(<Harness schema={schema} onFinish={onFinish} />)
    expect(screen.getByPlaceholderText('文件即将上线')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '提交' }))
    expect(onFinish).toHaveBeenCalledWith({})
    expect(JSON.stringify(onFinish.mock.calls)).not.toContain('mock/')
  })
})
