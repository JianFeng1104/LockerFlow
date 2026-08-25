<template>
  <BaseDialog :open="open" :title="title" title-id="confirm-title" @close="$emit('cancel')">
    <p class="text-sm leading-6 text-[var(--color-ink-700)]">{{ message }}</p>
    <div class="mt-6 flex justify-end gap-3">
      <button class="btn-secondary" type="button" :disabled="busy" @click="$emit('cancel')">取消</button>
      <button class="btn-danger" type="button" :disabled="busy" @click="$emit('confirm')">{{ busy ? '正在处理...' : confirmLabel }}</button>
    </div>
  </BaseDialog>
</template>

<script setup>
import BaseDialog from './BaseDialog.vue'
defineProps({ open: Boolean, title: String, message: String, confirmLabel: { type: String, default: '确认' }, busy: Boolean })
defineEmits(['confirm', 'cancel'])
</script>
