<script setup>
import StatusBadge from './StatusBadge.vue'
import AppIcon from './AppIcon.vue'
import { formatDateTime, formatRelativeExpiry, isPickupWindowOpen } from '../utils/format'

defineProps({ parcels: { type: Array, required: true } })
defineEmits(['pickup'])
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-2">
    <article v-for="parcel in parcels" :key="parcel.id" class="panel parcel-card">
      <div class="parcel-accent" :class="`parcel-accent-${parcel.status.toLowerCase()}`"></div>
      <div class="p-5">
      <div class="flex items-start justify-between gap-4">
        <div><p class="text-[10px] font-bold uppercase tracking-[0.12em] text-[var(--color-ink-500)]">快递单号</p><h3 class="tracking-code mt-1">{{ parcel.trackingNumber }}</h3></div>
        <StatusBadge :status="parcel.status" />
      </div>
      <dl class="mt-5 grid grid-cols-2 gap-4 rounded-[var(--radius-sm)] bg-[var(--color-surface)] p-4 text-sm">
        <div><dt>快递柜站点</dt><dd>{{ parcel.stationName || '暂未分配' }}</dd></div>
        <div><dt>柜格</dt><dd class="text-lg">{{ parcel.lockerCellCode || '—' }}</dd></div>
        <div class="col-span-2"><dt>取件截止时间</dt><dd>{{ formatDateTime(parcel.expiresAt) }}</dd><p v-if="parcel.status === 'STORED'" class="expiry-relative mt-1">{{ formatRelativeExpiry(parcel.expiresAt) }}</p></div>
      </dl>
      <p v-if="parcel.status === 'STORED' && !isPickupWindowOpen(parcel)" class="mt-4 text-sm font-bold text-amber-700">已超过取件时间</p>
      <button v-if="isPickupWindowOpen(parcel)" class="btn-primary mt-5 w-full" type="button" @click="$emit('pickup', parcel)">输入取件码 <AppIcon name="arrow" :size="16" /></button>
      <p v-else-if="parcel.status === 'PICKED_UP'" class="mt-4 flex items-center gap-2 text-sm font-bold text-[var(--color-info)]"><AppIcon name="check" :size="17" /> 取件已完成</p>
      <p v-else-if="parcel.status === 'EXPIRED'" class="mt-4 text-sm font-bold text-[var(--color-warning)]">已超过取件时间</p>
      </div>
    </article>
  </div>
</template>
