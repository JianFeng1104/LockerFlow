import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { useAuthStore } from '../stores/auth'

async function mountShell(path = '/courier/store') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().$patch({ initialized: true, user: { id: 2, username: 'courier.a', role: 'COURIER' }, accessToken: 'token' })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{
      path: '/courier',
      component: { template: '<div />' },
      children: [
        { path: '', component: { template: '<div />' }, meta: { title: '快递员工作台' } },
        { path: 'store', component: { template: '<div />' }, meta: { title: '包裹入柜' } },
        { path: 'parcels', component: { template: '<div />' }, meta: { title: '我的包裹' } },
      ],
    }],
  })
  await router.push(path)
  await router.isReady()
  return mount(AppShell, { global: { plugins: [pinia, router], stubs: { RouterView: true } } })
}

describe('AppShell', () => {
  beforeEach(() => document.body.innerHTML = '')

  it('marks the route-aware navigation item as active', async () => {
    const wrapper = await mountShell()
    const active = wrapper.find('.nav-link.is-active')
    expect(active.text()).toContain('包裹入柜')
    expect(active.attributes('aria-current')).toBe('page')
    wrapper.unmount()
  })

  it('opens the mobile drawer and closes it with Escape', async () => {
    const wrapper = await mountShell()
    await wrapper.find('[aria-label="打开导航菜单"]').trigger('click')
    expect(wrapper.find('.app-sidebar').classes()).toContain('is-open')
    expect(wrapper.find('.sidebar-overlay').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.app-sidebar').classes()).not.toContain('is-open')
    wrapper.unmount()
  })
})
