<template>
  <span class="status-badge" :class="`status-${normalized.toLowerCase()}`">
    <span class="status-dot" aria-hidden="true"></span>
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { statusText } from '../utils/displayText'

const props = defineProps({ status: { type: String, required: true } })
const normalized = computed(() => props.status || 'UNKNOWN')
const label = computed(() => statusText(normalized.value))
</script>
