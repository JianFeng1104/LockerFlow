import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api/stations', () => ({ getStations: vi.fn() }))
vi.mock('../api/parcels', () => ({ storeParcel: vi.fn() }))

import { getStations } from '../api/stations'
import { storeParcel } from '../api/parcels'
import StoreParcelView from '../views/courier/StoreParcelView.vue'

const station = { id: 1, name: 'Central Station', status: 'ACTIVE' }
const storedResponse = {
  parcel: {
    id: 10,
    trackingNumber: 'PKG-TEST-1',
    stationName: 'Central Station',
    lockerCellCode: 'A01',
    lockerCellSize: 'SMALL',
    expiresAt: '2099-01-01T00:00:00Z',
  },
  pickupCode: '654321',
  pickupCodeExpiresAt: '2099-01-01T00:00:00Z',
}

async function mountReady() {
  getStations.mockResolvedValue([station])
  const wrapper = mount(StoreParcelView, {
    global: { plugins: [createPinia()], stubs: { Teleport: true } },
  })
  await flushPromises()
  const inputs = wrapper.findAll('input')
  await inputs[0].setValue('PKG-TEST-1')
  await inputs[1].setValue('5')
  const selects = wrapper.findAll('select')
  await selects[0].setValue('1')
  await selects[1].setValue('SMALL')
  return wrapper
}

describe('StoreParcelView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('shows a one-time code after success without persisting it in browser storage', async () => {
    storeParcel.mockResolvedValue(storedResponse)
    const wrapper = await mountReady()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('一次性取件码')
    expect(wrapper.text()).toContain('654321')
    expect(Array.from({ length: sessionStorage.length }, (_, index) => sessionStorage.getItem(sessionStorage.key(index))).join(' ')).not.toContain('654321')
    const close = wrapper.findAll('button').find((button) => button.text() === '关闭并清除取件码')
    await close.trigger('click')
    expect(wrapper.text()).not.toContain('654321')
    wrapper.unmount()
  })

  it.each([
    [400, 'Validation failed', '提交内容校验失败'],
    [401, 'Authentication is required', '请先登录'],
    [403, 'Access is denied', '无权访问'],
    [409, 'No suitable locker cell is available', '没有可用的合适柜格'],
  ])('renders API status %s safely', async (status, backendMessage, expectedMessage) => {
    storeParcel.mockRejectedValue({ response: { status, data: { status, message: backendMessage, fieldErrors: status === 400 ? { trackingNumber: 'invalid' } : {} } } })
    const wrapper = await mountReady()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain(expectedMessage)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
