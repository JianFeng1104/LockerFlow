const FRIENDLY_MESSAGES = Object.freeze({
  'Invalid username or password': '用户名或密码错误',
  'Authentication is required': '请先登录',
  'Access is denied': '无权访问',
  'Validation failed': '提交内容校验失败',
  'No suitable locker cell is available': '没有可用的合适柜格',
  'Invalid or expired pickup code': '取件码无效或已过期',
})

function friendlyMessage(message, fallback) {
  if (typeof message !== 'string') return fallback
  return FRIENDLY_MESSAGES[message] ?? message
}

export function normalizeApiError(error, fallback = '出现错误') {
  const data = error?.response?.data
  return {
    status: error?.response?.status ?? data?.status ?? 0,
    message: friendlyMessage(data?.message, fallback),
    fieldErrors: data?.fieldErrors && typeof data.fieldErrors === 'object' ? data.fieldErrors : {},
  }
}
