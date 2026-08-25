<script setup>
import { computed } from 'vue'

const props = defineProps({
  total: { type: Number, required: true },
  available: { type: Number, default: 0 },
  occupied: { type: Number, default: 0 },
  maintenance: { type: Number, default: 0 },
  disabled: { type: Number, default: 0 },
})

const segments = computed(() => [
  { name: '空闲', value: props.available, className: 'capacity-available' },
  { name: '已占用', value: props.occupied, className: 'capacity-occupied' },
  { name: '维护中', value: props.maintenance, className: 'capacity-maintenance' },
  { name: '已停用', value: props.disabled, className: 'capacity-disabled' },
])

function width(value) {
  return `${props.total > 0 ? Math.max(0, (value / props.total) * 100) : 0}%`
}
</script>

<template>
  <div class="capacity-track" role="img" :aria-label="`共 ${total} 个柜格，其中空闲 ${available} 个、已占用 ${occupied} 个、维护中 ${maintenance} 个、已停用 ${disabled} 个`">
    <span v-for="segment in segments" :key="segment.name" class="capacity-segment" :class="segment.className" :style="{ width: width(segment.value) }"></span>
  </div>
</template>
