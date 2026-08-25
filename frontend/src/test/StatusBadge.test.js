import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusBadge from '../components/StatusBadge.vue'

describe('StatusBadge', () => {
  it('shows a Chinese status label alongside the visual dot', () => {
    const wrapper = mount(StatusBadge, { props: { status: 'PICKED_UP' } })
    expect(wrapper.text()).toBe('已取件')
    expect(wrapper.find('.status-dot').attributes('aria-hidden')).toBe('true')
    expect(wrapper.classes()).toContain('status-picked_up')
  })
})
