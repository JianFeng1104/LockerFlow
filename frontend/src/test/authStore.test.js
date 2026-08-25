import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../api/auth', () => ({ login: vi.fn(), getCurrentUser: vi.fn() }))

import * as authApi from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { saveStoredSession } from '../utils/authStorage'

const future = '2099-08-23T12:00:00Z'
const user = { id: 7, username: 'courier.a', role: 'COURIER', status: 'ACTIVE' }
const loginResponse = { accessToken: 'test-token', expiresAt: future, user }

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('loads the authenticated user on successful login', async () => {
    authApi.login.mockResolvedValue(loginResponse)
    const store = useAuthStore()
    await store.login({ username: 'courier.a', password: 'password' })
    expect(store.user).toEqual(user)
    expect(store.isAuthenticated).toBe(true)
  })

  it('stores only the access token and expiry in sessionStorage', async () => {
    authApi.login.mockResolvedValue(loginResponse)
    await useAuthStore().login({ username: 'courier.a', password: 'password' })
    expect(sessionStorage.getItem('lockerflow.accessToken')).toBe('test-token')
    expect(sessionStorage.getItem('lockerflow.expiresAt')).toBe(future)
    expect(sessionStorage.length).toBe(2)
  })

  it('never persists the password', async () => {
    authApi.login.mockResolvedValue(loginResponse)
    await useAuthStore().login({ username: 'courier.a', password: 'never-store-this' })
    const persisted = Array.from({ length: sessionStorage.length }, (_, index) => sessionStorage.getItem(sessionStorage.key(index))).join(' ')
    expect(persisted).not.toContain('never-store-this')
  })

  it('restores a valid session through auth me', async () => {
    saveStoredSession(loginResponse)
    authApi.getCurrentUser.mockResolvedValue(user)
    const store = useAuthStore()
    await store.restoreSession()
    expect(authApi.getCurrentUser).toHaveBeenCalledOnce()
    expect(store.user).toEqual(user)
    expect(store.initialized).toBe(true)
  })

  it('clears an expired session without calling auth me', async () => {
    sessionStorage.setItem('lockerflow.accessToken', 'expired-token')
    sessionStorage.setItem('lockerflow.expiresAt', '2000-01-01T00:00:00Z')
    const store = useAuthStore()
    await store.restoreSession()
    expect(authApi.getCurrentUser).not.toHaveBeenCalled()
    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.length).toBe(0)
  })

  it('clears a session when auth me returns 401', async () => {
    saveStoredSession(loginResponse)
    authApi.getCurrentUser.mockRejectedValue({ response: { status: 401 } })
    const store = useAuthStore()
    await expect(store.restoreSession()).rejects.toBeTruthy()
    expect(store.accessToken).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('logout clears state and browser storage', () => {
    saveStoredSession(loginResponse)
    const store = useAuthStore()
    store.$patch({ user, accessToken: 'test-token', expiresAt: future })
    store.logout()
    expect(store.user).toBeNull()
    expect(store.accessToken).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })
})
