import { describe, expect, it } from 'vitest'
import { roleText, sizeText, statusText } from '../utils/displayText'

describe('中文显示映射', () => {
  it.each([
    ['AVAILABLE', '空闲'],
    ['OCCUPIED', '已占用'],
    ['MAINTENANCE', '维护中'],
    ['DISABLED', '已停用'],
    ['STORED', '待取件'],
    ['PICKED_UP', '已取件'],
    ['EXPIRED', '已过期'],
  ])('将状态 %s 显示为 %s', (value, expected) => {
    expect(statusText(value)).toBe(expected)
  })

  it.each([
    ['ADMIN', '管理员'],
    ['COURIER', '快递员'],
    ['CUSTOMER', '用户'],
  ])('将角色 %s 显示为 %s', (value, expected) => {
    expect(roleText(value)).toBe(expected)
  })

  it.each([
    ['SMALL', '小型'],
    ['MEDIUM', '中型'],
    ['LARGE', '大型'],
  ])('将尺寸 %s 显示为 %s', (value, expected) => {
    expect(sizeText(value)).toBe(expected)
  })

  it('为未知值提供安全回退', () => {
    expect(statusText('UNKNOWN')).toBe('UNKNOWN')
    expect(roleText('UNKNOWN')).toBe('UNKNOWN')
    expect(sizeText('UNKNOWN')).toBe('UNKNOWN')
  })
})

