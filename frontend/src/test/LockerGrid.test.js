import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import LockerGrid from '../components/LockerGrid.vue'

describe('LockerGrid', () => {
  it('renders all four cell states with status text', () => {
    const cells = ['AVAILABLE', 'OCCUPIED', 'MAINTENANCE', 'DISABLED'].map((status, index) => ({ id: index + 1, cellCode: `A0${index + 1}`, size: 'SMALL', status, version: 0 }))
    const wrapper = mount(LockerGrid, { props: { cells } })
    const labels = { AVAILABLE: '空闲', OCCUPIED: '已占用', MAINTENANCE: '维护中', DISABLED: '已停用' }
    for (const status of ['AVAILABLE', 'OCCUPIED', 'MAINTENANCE', 'DISABLED']) {
      expect(wrapper.text()).toContain(labels[status])
      expect(wrapper.find(`.cell-${status.toLowerCase()}`).exists()).toBe(true)
    }
  })

  it('filters the already-loaded grid without changing the supplied data', async () => {
    const cells = ['AVAILABLE', 'OCCUPIED', 'MAINTENANCE', 'DISABLED'].map((status, index) => ({ id: index + 1, cellCode: `B0${index + 1}`, size: 'MEDIUM', status, version: 0 }))
    const wrapper = mount(LockerGrid, { props: { cells } })
    const occupiedFilter = wrapper.findAll('.filter-chip').find((button) => button.text().includes('已占用'))
    await occupiedFilter.trigger('click')
    expect(wrapper.findAll('.locker-cell')).toHaveLength(1)
    expect(wrapper.find('.locker-cell').attributes('aria-label')).toContain('已占用')
    expect(cells).toHaveLength(4)
  })
})
