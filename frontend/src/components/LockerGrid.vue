<template>
  <div>
    <div class="locker-toolbar">
      <div class="locker-filters" aria-label="筛选柜格">
        <button v-for="option in filterOptions" :key="option.value" class="filter-chip" :class="{ 'is-active': filter === option.value }" type="button" :aria-pressed="filter === option.value" @click="filter = option.value">
          <span v-if="option.value !== 'ALL'" class="legend-dot" :class="`legend-${option.value.toLowerCase()}`" aria-hidden="true"></span>
          {{ option.label }} <span>{{ option.count }}</span>
        </button>
      </div>
      <p class="locker-count">显示 <strong>{{ filteredCells.length }}</strong> / {{ cells.length }} 个柜格</p>
    </div>
    <div class="locker-grid" aria-label="智能柜格列表">
      <button v-for="cell in filteredCells" :key="cell.id" type="button" class="locker-cell" :class="`cell-${cell.status.toLowerCase()}`" :aria-label="`${cell.cellCode}，${sizeText(cell.size)}，${statusText(cell.status)}`" @click="$emit('select', cell)">
        <span class="locker-code">{{ cell.cellCode }}</span>
        <span class="locker-size">{{ sizeText(cell.size) }}</span>
        <span class="locker-door-panel" aria-hidden="true"></span>
        <span class="locker-status">{{ statusText(cell.status) }}</span>
      </button>
      <div v-if="!filteredCells.length" class="state-panel locker-empty-filter">
        <p class="font-bold text-[var(--color-ink-950)]">没有符合条件的柜格</p>
        <p class="mt-1 text-sm">请选择其他状态查看实时柜格。</p>
        <button class="btn-secondary mt-4" type="button" @click="filter = 'ALL'">显示全部柜格</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { sizeText, statusText } from '../utils/displayText'

const props = defineProps({ cells: { type: Array, required: true } })
defineEmits(['select'])
const filter = ref('ALL')
const states = ['AVAILABLE', 'OCCUPIED', 'MAINTENANCE', 'DISABLED']
const filteredCells = computed(() => filter.value === 'ALL' ? props.cells : props.cells.filter((cell) => cell.status === filter.value))
const filterOptions = computed(() => [
  { value: 'ALL', label: '全部', count: props.cells.length },
  ...states.map((state) => ({ value: state, label: statusText(state), count: props.cells.filter((cell) => cell.status === state).length })),
])
</script>
