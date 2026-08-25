import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../api/stations', () => ({ getStations: vi.fn() }))
vi.mock('../api/operations', () => ({ runExpirationProcessing: vi.fn() }))

import { getStations } from '../api/stations'
import AdminDashboardView from '../views/admin/AdminDashboardView.vue'

describe('AdminDashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('renders only API-derived capacity metrics with no fallback trend data', async () => {
    getStations.mockResolvedValue([{
      id: 9,
      name: 'API Station Nine',
      address: 'Live API address',
      status: 'ACTIVE',
      totalCells: 8,
      availableCells: 2,
      occupiedCells: 3,
      maintenanceCells: 2,
      disabledCells: 1,
    }])
    const wrapper = mount(AdminDashboardView, {
      global: { plugins: [createPinia()], stubs: { RouterLink: { template: '<a><slot /></a>' }, Teleport: true } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('API Station Nine')
    expect(wrapper.text()).toContain('25%')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).not.toMatch(/last month|\+\d+%/i)
    wrapper.unmount()
  })
})
