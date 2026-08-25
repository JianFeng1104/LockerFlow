<template>
  <div class="fixed right-4 top-4 z-[100] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2" aria-live="polite">
    <TransitionGroup name="toast-list">
      <button v-for="toast in app.toasts" :key="toast.id" class="toast" :class="`toast-${toast.type}`" type="button" :aria-label="`关闭通知：${toast.message}`" @click="app.dismiss(toast.id)">
        <span class="toast-icon"><AppIcon :name="toast.type === 'success' ? 'check' : toast.type === 'error' ? 'error' : 'info'" /></span>
        <span><strong>{{ toast.type === 'success' ? '操作成功' : toast.type === 'error' ? '操作失败' : '提示' }}</strong><small>{{ toast.message }}</small></span>
        <AppIcon class="toast-close" name="close" :size="15" />
      </button>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { useAppStore } from '../stores/app'
import AppIcon from './AppIcon.vue'
const app = useAppStore()
</script>
