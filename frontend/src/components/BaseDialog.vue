<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="open" class="dialog-backdrop" @click.self="requestClose">
        <section ref="panel" class="dialog-panel" role="dialog" aria-modal="true" :aria-labelledby="titleId" tabindex="-1">
          <header class="flex items-start justify-between gap-4">
            <div><p class="dialog-eyebrow">LockerFlow 工作平台</p><h2 :id="titleId" class="mt-1 text-xl font-black tracking-[-0.025em]">{{ title }}</h2><slot name="description" /></div>
            <button class="icon-button" type="button" aria-label="关闭对话框" @click="requestClose"><AppIcon name="close" /></button>
          </header>
          <div class="mt-5"><slot /></div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'

defineProps({ open: Boolean, title: { type: String, required: true }, titleId: { type: String, default: 'dialog-title' } })
const emit = defineEmits(['close'])
const panel = ref(null)
let previousFocus = null

function requestClose() { emit('close') }
function handleKeydown(event) { if (event.key === 'Escape') requestClose() }

watch(() => panel.value, async (value) => {
  if (!value) return
  previousFocus = document.activeElement
  document.addEventListener('keydown', handleKeydown)
  await nextTick()
  value.focus()
}, { flush: 'post' })

watch(() => panel.value, (value, oldValue) => {
  if (!value && oldValue) {
    document.removeEventListener('keydown', handleKeydown)
    previousFocus?.focus?.()
  }
})

onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))
</script>
