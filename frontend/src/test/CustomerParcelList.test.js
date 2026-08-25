import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CustomerParcelList from '../components/CustomerParcelList.vue'

describe('CustomerParcelList', () => {
  it('offers pickup only for a stored parcel with an open window', () => {
    const parcels = [
      { id: 1, trackingNumber: 'STORED-1', status: 'STORED', stationName: 'Station', lockerCellCode: 'A01', expiresAt: '2099-01-01T00:00:00Z' },
      { id: 2, trackingNumber: 'PICKED-1', status: 'PICKED_UP', stationName: 'Station', lockerCellCode: 'A02', expiresAt: '2099-01-01T00:00:00Z' },
      { id: 3, trackingNumber: 'EXPIRED-1', status: 'EXPIRED', stationName: 'Station', lockerCellCode: 'A03', expiresAt: '2000-01-01T00:00:00Z' },
    ]
    const wrapper = mount(CustomerParcelList, { props: { parcels } })
    expect(wrapper.findAll('button')).toHaveLength(1)
    expect(wrapper.find('button').text()).toContain('输入取件码')
  })
})
