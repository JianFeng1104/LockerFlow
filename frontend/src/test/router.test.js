import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter, resolvePostLoginRedirect } from '../router'
import { useAuthStore } from '../stores/auth'

function authenticate(role) {
  const store = useAuthStore()
  store.$patch({ initialized: true, user: { id: 1, username: 'test', role }, accessToken: 'test-token' })
}

async function navigate(path) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  await router.isReady()
  return router
}

describe('router auth and role guard', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('sends an anonymous protected request to login', async () => {
    useAuthStore().$patch({ initialized: true })
    const router = await navigate('/customer/parcels')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/customer/parcels')
  })

  it.each([['ADMIN', '/admin'], ['COURIER', '/courier'], ['CUSTOMER', '/customer']])('redirects %s from root to its dashboard', async (role, expected) => {
    authenticate(role)
    expect((await navigate('/')).currentRoute.value.path).toBe(expected)
  })

  it('returns a customer trying admin routes to customer home', async () => {
    authenticate('CUSTOMER')
    expect((await navigate('/admin')).currentRoute.value.path).toBe('/customer')
  })

  it('returns a courier trying customer routes to courier home', async () => {
    authenticate('COURIER')
    expect((await navigate('/customer')).currentRoute.value.path).toBe('/courier')
  })

  it('rejects external and protocol-relative login redirects', () => {
    authenticate('CUSTOMER')
    const router = createAppRouter(createMemoryHistory())
    expect(resolvePostLoginRedirect(router, 'https://evil.example', 'CUSTOMER')).toBe('/customer')
    expect(resolvePostLoginRedirect(router, '//evil.example', 'CUSTOMER')).toBe('/customer')
    expect(resolvePostLoginRedirect(router, '/unknown-route', 'CUSTOMER')).toBe('/customer')
  })
})
