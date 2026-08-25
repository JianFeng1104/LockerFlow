import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BaseDialog from '../components/BaseDialog.vue'

describe('BaseDialog', () => {
  it('associates its heading and requests close on Escape', async () => {
    const wrapper = mount(BaseDialog, {
      props: { open: true, title: 'Operational details', titleId: 'operation-heading' },
      global: { stubs: { Teleport: true, Transition: false } },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[role="dialog"]').attributes('aria-labelledby')).toBe('operation-heading')
    expect(wrapper.find('#operation-heading').text()).toBe('Operational details')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
})
