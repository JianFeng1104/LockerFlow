export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export function isPickupWindowOpen(parcel, now = Date.now()) {
  return parcel?.status === 'STORED' && Date.parse(parcel.expiresAt) > now
}

export function formatRelativeExpiry(value, now = Date.now()) {
  const expiresAt = Date.parse(value)
  if (!Number.isFinite(expiresAt)) return '取件截止时间不可用'
  const milliseconds = expiresAt - now
  if (milliseconds <= 0) return '已超过取件时间'
  const minutes = Math.ceil(milliseconds / 60_000)
  if (minutes < 60) return `剩余 ${minutes} 分钟`
  const hours = Math.ceil(minutes / 60)
  if (hours < 48) return `剩余 ${hours} 小时`
  const days = Math.ceil(hours / 24)
  return `剩余 ${days} 天`
}
