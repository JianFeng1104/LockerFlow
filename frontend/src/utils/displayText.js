const STATUS_TEXT = Object.freeze({
  ACTIVE: '正常',
  AVAILABLE: '空闲',
  OCCUPIED: '已占用',
  MAINTENANCE: '维护中',
  DISABLED: '已停用',
  CREATED: '已创建',
  STORED: '待取件',
  PICKED_UP: '已取件',
  EXPIRED: '已过期',
  ABNORMAL: '异常',
  CANCELLED: '已取消',
  USED: '已使用',
  REVOKED: '已撤销',
})

const ROLE_TEXT = Object.freeze({
  ADMIN: '管理员',
  COURIER: '快递员',
  CUSTOMER: '用户',
})

const SIZE_TEXT = Object.freeze({
  SMALL: '小型',
  MEDIUM: '中型',
  LARGE: '大型',
})

function mappedText(mapping, value) {
  if (typeof value !== 'string' || !value) return '—'
  return mapping[value] ?? value
}

export function statusText(value) {
  return mappedText(STATUS_TEXT, value)
}

export function roleText(value) {
  return mappedText(ROLE_TEXT, value)
}

export function sizeText(value) {
  return mappedText(SIZE_TEXT, value)
}

