<template>
  <div class="state-panel">
    <span class="state-icon" aria-hidden="true"><AppIcon name="empty" :size="24" /></span>
    <p class="mt-3 font-bold text-[var(--color-ink-950)]">{{ title }}</p>
    <p class="mt-1 text-sm">{{ message }}</p>
    <div v-if="$slots.default" class="mt-4"><slot /></div>
  </div>
</template>

<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ title: { type: String, default: '暂无数据' }, message: { type: String, required: true } })
</script>
