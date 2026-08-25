import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('../api/parcels', () => ({ getCustomerParcels: vi.fn(), pickupParcel: vi.fn() }))

import { getCustomerParcels } from '../api/parcels'
import LoginView from '../views/LoginView.vue'
import CustomerParcelsView from '../views/customer/CustomerParcelsView.vue'

describe('主要页面中文文案', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('登录页面显示中文文案', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/login', component: LoginView }],
    })
    await router.push('/login')
    await router.isReady()
    const wrapper = mount(LoginView, { global: { plugins: [createPinia(), router] } })
    expect(wrapper.text()).toContain('欢迎回来')
    expect(wrapper.text()).toContain('用户名')
    expect(wrapper.text()).toContain('密码')
    expect(wrapper.find('button[type="submit"]').text()).toContain('登录')
    wrapper.unmount()
  })

  it('用户取件页面显示中文操作文案', async () => {
    getCustomerParcels.mockResolvedValue([{
      id: 1,
      trackingNumber: 'PKG-CN-1',
      status: 'STORED',
      stationName: '中央站点',
      lockerCellCode: 'A01',
      expiresAt: '2099-01-01T00:00:00Z',
    }])
    const wrapper = mount(CustomerParcelsView, {
      global: { plugins: [createPinia()], stubs: { Teleport: true } },
    })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('输入取件码')).trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('包裹取件')
    expect(wrapper.text()).toContain('确认取件')
    wrapper.unmount()
  })
})
