import { beforeEach, describe, expect, it, vi } from 'vitest'
import http, { configureHttpAuth } from '../api/http'
import { saveStoredSession } from '../utils/authStorage'

const future = '2099-01-01T00:00:00Z'

function successfulAdapter(capture) {
  return async (config) => {
    capture.value = config
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
  }
}

function failingAdapter(status) {
  return async (config) => Promise.reject({ config, response: { status, data: {} } })
}

describe('Axios authentication integration', () => {
  beforeEach(() => configureHttpAuth())

  it('adds a Bearer token to authenticated API requests', async () => {
    saveStoredSession({ accessToken: 'test-token', expiresAt: future })
    const capture = {}
    await http.get('/courier/parcels', { adapter: successfulAdapter(capture) })
    expect(capture.value.headers.Authorization).toBe('Bearer test-token')
  })

  it('does not force a stored token onto login', async () => {
    saveStoredSession({ accessToken: 'stale-token', expiresAt: future })
    const capture = {}
    await http.post('/auth/login', {}, { adapter: successfulAdapter(capture) })
    expect(capture.value.headers.Authorization).toBeUndefined()
  })

  it('clears session and notifies the app on non-login 401', async () => {
    saveStoredSession({ accessToken: 'test-token', expiresAt: future })
    const onUnauthorized = vi.fn()
    configureHttpAuth({ onUnauthorized })
    await expect(http.get('/auth/me', { adapter: failingAdapter(401) })).rejects.toBeTruthy()
    expect(sessionStorage.length).toBe(0)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('retains the session and does not log out on 403', async () => {
    saveStoredSession({ accessToken: 'test-token', expiresAt: future })
    const onUnauthorized = vi.fn()
    configureHttpAuth({ onUnauthorized })
    await expect(http.get('/admin/stations', { adapter: failingAdapter(403) })).rejects.toBeTruthy()
    expect(sessionStorage.getItem('lockerflow.accessToken')).toBe('test-token')
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
