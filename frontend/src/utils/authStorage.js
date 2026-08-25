const ACCESS_TOKEN_KEY = 'lockerflow.accessToken'
const EXPIRES_AT_KEY = 'lockerflow.expiresAt'

function browserStorage() {
  return typeof window === 'undefined' ? null : window.sessionStorage
}

export function saveStoredSession({ accessToken, expiresAt }) {
  const storage = browserStorage()
  if (!storage || !accessToken || !expiresAt) return
  storage.setItem(ACCESS_TOKEN_KEY, accessToken)
  storage.setItem(EXPIRES_AT_KEY, expiresAt)
}

export function getStoredSession(now = Date.now()) {
  const storage = browserStorage()
  if (!storage) return null
  const accessToken = storage.getItem(ACCESS_TOKEN_KEY)
  const expiresAt = storage.getItem(EXPIRES_AT_KEY)
  const expiryTime = expiresAt ? Date.parse(expiresAt) : Number.NaN
  if (!accessToken || !Number.isFinite(expiryTime) || expiryTime <= now) {
    clearStoredSession()
    return null
  }
  return { accessToken, expiresAt }
}

export function clearStoredSession() {
  const storage = browserStorage()
  storage?.removeItem(ACCESS_TOKEN_KEY)
  storage?.removeItem(EXPIRES_AT_KEY)
}
