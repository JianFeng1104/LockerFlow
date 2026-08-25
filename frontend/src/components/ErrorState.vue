<template>
  <div class="state-panel state-error" role="alert">
    <span class="state-icon state-icon-error" aria-hidden="true"><AppIcon name="error" :size="24" /></span>
    <p class="mt-3 font-bold text-[var(--color-ink-950)]">页面加载失败</p>
    <p class="mt-1 text-sm">{{ message }}</p>
    <button v-if="retryable" class="btn-secondary mt-4" type="button" @click="$emit('retry')">重试</button>
  </div>
</template>

<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ message: { type: String, required: true }, retryable: { type: Boolean, default: true } })
defineEmits(['retry'])
</script>
