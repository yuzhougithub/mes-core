/** List display for phone numbers: `138****5678`. */
export function maskMobile(value: string | null | undefined): string {
  if (value == null) return '—'
  const text = value.trim()
  if (!text) return '—'
  const chars = Array.from(text)
  if (chars.length <= 2) return '*'.repeat(chars.length)
  if (chars.length < 7) return `${chars[0]}***${chars[chars.length - 1]}`
  return `${chars.slice(0, 3).join('')}****${chars.slice(-4).join('')}`
}

/** List display for mailboxes: `z***@x.com`. */
export function maskEmail(value: string | null | undefined): string {
  if (value == null) return '—'
  const text = value.trim()
  if (!text) return '—'
  const at = text.indexOf('@')
  if (at <= 0 || at === text.length - 1) return '***'
  return `${Array.from(text)[0]}***@${text.slice(at + 1)}`
}
