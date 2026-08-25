<template>
  <div class="state-panel" role="status">
    <span class="loading-spinner" aria-hidden="true"></span>
    <p class="font-bold text-[var(--color-ink-700)]">{{ message }}</p>
    <span class="sr-only">正在加载实时数据，请稍候。</span>
    <div class="skeleton-lines" aria-hidden="true"><span></span><span></span><span></span></div>
  </div>
</template>

<script setup>
defineProps({ message: { type: String, default: '加载中...' } })
</script>
