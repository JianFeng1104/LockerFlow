import { describe, expect, it } from 'vitest'
import { formatDateTime, formatRelativeExpiry } from '../utils/format'

describe('中文时间显示', () => {
  const now = Date.parse('2026-08-25T06:00:00Z')

  it.each([
    [5 * 60_000, '剩余 5 分钟'],
    [2 * 60 * 60_000, '剩余 2 小时'],
    [3 * 24 * 60 * 60_000, '剩余 3 天'],
  ])('将相对到期时间显示为中文', (offset, expected) => {
    expect(formatRelativeExpiry(new Date(now + offset).toISOString(), now)).toBe(expected)
  })

  it('显示中文友好的绝对日期', () => {
    const result = formatDateTime('2026-08-25T06:30:00Z')
    expect(result).toContain('2026年8月25日')
    expect(result).toContain('14:30')
  })

  it('显示中文过期状态', () => {
    expect(formatRelativeExpiry('2026-08-25T05:59:00Z', now)).toBe('已超过取件时间')
  })
})
