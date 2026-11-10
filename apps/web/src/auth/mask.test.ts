import { describe, expect, it } from 'vitest'
import { maskEmail, maskMobile } from './mask.ts'

describe('masking', () => {
  it('masks a phone number as 138****5678', () => {
    expect(maskMobile('13812345678')).toBe('138****5678')
    expect(maskMobile(' 13812345678 ')).toBe('138****5678')
    expect(maskMobile('')).toBe('—')
    expect(maskMobile(null)).toBe('—')
  })

  it('masks an email as z***@x.com', () => {
    expect(maskEmail('zhang@x.com')).toBe('z***@x.com')
    expect(maskEmail('ab@example.com')).toBe('a***@example.com')
    expect(maskEmail('not-an-email')).toBe('***')
    expect(maskEmail(undefined)).toBe('—')
  })
})
