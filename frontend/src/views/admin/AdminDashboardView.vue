<script setup>
import { computed, onMounted, ref } from 'vue'
import { getStations } from '../../api/stations'
import { runExpirationProcessing } from '../../api/operations'
import { normalizeApiError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/format'
import { useAppStore } from '../../stores/app'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import CapacityBar from '../../components/CapacityBar.vue'
import AppIcon from '../../components/AppIcon.vue'

const app = useAppStore()
const stations = ref([])
const loading = ref(true)
const refreshing = ref(false)
const errorMessage = ref('')
const operationOpen = ref(false)
const running = ref(false)
const operationResult = ref(null)

const totals = computed(() => stations.value.reduce((summary, station) => {
  summary.total += station.totalCells
  summary.available += station.availableCells
  summary.occupied += station.occupiedCells
  summary.maintenance += station.maintenanceCells
  summary.disabled += station.disabledCells
  return summary
}, { total: 0, available: 0, occupied: 0, maintenance: 0, disabled: 0 }))
const availabilityRate = computed(() => totals.value.total > 0 ? Math.round((totals.value.available / totals.value.total) * 100) : 0)

async function load(refresh = false) {
  refresh ? (refreshing.value = true) : (loading.value = true)
  errorMessage.value = ''
  try { stations.value = await getStations() }
  catch (error) { errorMessage.value = normalizeApiError(error, '站点数据加载失败').message }
  finally { loading.value = false; refreshing.value = false }
}

async function runExpiration() {
  running.value = true
  try {
    operationResult.value = await runExpirationProcessing()
    operationOpen.value = false
    app.notify('过期处理完成', 'success')
  } catch (error) { app.notify(normalizeApiError(error, '过期处理失败').message, 'error') }
  finally { running.value = false }
}

onMounted(() => load())
</script>

<template>
  <section>
    <PageHeader title="管理员工作台" eyebrow="管理员" description="查看 LockerFlow 的实时站点容量并执行生命周期运维操作。">
      <button class="btn-secondary" type="button" :disabled="refreshing" @click="load(true)"><AppIcon name="refresh" :size="16" />{{ refreshing ? '正在刷新...' : '刷新' }}</button>
    </PageHeader>
    <LoadingState v-if="loading" class="mt-7" />
    <ErrorState v-else-if="errorMessage" class="mt-7" :message="errorMessage" @retry="load()" />
    <div v-else class="mt-7 space-y-6">
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-12">
        <article class="panel surface-dark relative overflow-hidden p-6 sm:col-span-2 xl:col-span-6 xl:row-span-2">
          <div class="flex items-start justify-between gap-4"><div><p class="text-xs font-bold uppercase tracking-[.15em] text-emerald-300">柜格容量</p><h2 class="mt-2 text-xl font-black">全网空闲率</h2></div><span class="grid size-11 place-items-center rounded-xl bg-white/10 text-emerald-300"><AppIcon name="locker" :size="23" /></span></div>
          <div class="mt-8 flex items-end justify-between gap-5"><div><p class="metric-number text-5xl font-black">{{ availabilityRate }}<span class="text-2xl text-emerald-300">%</span></p><p class="muted mt-2 text-sm">共 {{ totals.total }} 个柜格，空闲 {{ totals.available }} 个</p></div><div class="text-right"><p class="metric-number text-2xl font-black">{{ totals.total }}</p><p class="muted text-xs">柜格总数</p></div></div>
          <CapacityBar class="mt-6" :total="totals.total" :available="totals.available" :occupied="totals.occupied" :maintenance="totals.maintenance" :disabled="totals.disabled" />
          <div class="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-[11px] text-slate-300"><span><i class="legend-dot legend-available mr-2 inline-block"></i>空闲 {{ totals.available }}</span><span><i class="legend-dot legend-occupied mr-2 inline-block"></i>已占用 {{ totals.occupied }}</span><span><i class="legend-dot legend-maintenance mr-2 inline-block"></i>维护中 {{ totals.maintenance }}</span><span><i class="legend-dot legend-disabled mr-2 inline-block"></i>已停用 {{ totals.disabled }}</span></div>
        </article>
        <article v-for="item in [
          { label: '空闲', value: totals.available, note: '可供系统分配', color: 'var(--color-brand)', glow: 'rgba(13,128,111,.1)' },
          { label: '已占用', value: totals.occupied, note: '正在包裹流程中使用', color: 'var(--color-warning)', glow: 'rgba(229,157,59,.12)' },
          { label: '维护中', value: totals.maintenance, note: '需要运维处理', color: '#a75d24', glow: 'rgba(183,105,42,.1)' },
          { label: '已停用', value: totals.disabled, note: '当前不提供服务', color: '#66758a', glow: 'rgba(102,117,138,.1)' },
        ]" :key="item.label" class="panel metric-card p-5 xl:col-span-3" :style="{ '--metric-glow': item.glow }">
          <span class="block h-1 w-8 rounded-full" :style="{ background: item.color }"></span><p class="mt-4 text-xs font-bold uppercase tracking-[.1em] text-[var(--color-ink-500)]">{{ item.label }}</p><p class="metric-number mt-2 text-3xl font-black">{{ item.value }}</p><p class="mt-2 text-xs text-[var(--color-ink-500)]">{{ item.note }}</p>
        </article>
      </div>

      <div class="grid gap-6 xl:grid-cols-[1.4fr_0.6fr]">
        <div class="panel p-5 sm:p-6">
          <div class="flex items-center justify-between"><div><p class="text-[10px] font-bold uppercase tracking-[.14em] text-[var(--color-brand)]">站点运行状态</p><h2 class="mt-1 text-lg font-black">快递柜站点 <span class="ml-1 text-sm font-semibold text-[var(--color-ink-500)]">{{ stations.length }}</span></h2></div><RouterLink class="section-link" to="/admin/stations">管理全部站点 <AppIcon name="arrow" :size="15" /></RouterLink></div>
          <EmptyState v-if="!stations.length" class="mt-4" message="新建站点后即可开始管理柜格容量。" />
          <div v-else class="mt-4 divide-y divide-[var(--color-line)]">
            <RouterLink v-for="station in stations.slice(0, 6)" :key="station.id" :to="`/admin/stations/${station.id}`" class="group grid gap-3 py-4 no-underline sm:grid-cols-[1fr_12rem_auto] sm:items-center">
              <div><p class="font-bold text-[var(--color-ink-950)] group-hover:text-[var(--color-brand-deep)]">{{ station.name }}</p><p class="mt-1 text-xs text-[var(--color-ink-500)]">{{ station.address }}</p></div>
              <div><CapacityBar :total="station.totalCells" :available="station.availableCells" :occupied="station.occupiedCells" :maintenance="station.maintenanceCells" :disabled="station.disabledCells" /><p class="mt-1 text-[10px] text-[var(--color-ink-500)]">空闲 {{ station.availableCells }} 个 · 共 {{ station.totalCells }} 个</p></div>
              <StatusBadge :status="station.status" />
            </RouterLink>
          </div>
        </div>
        <aside class="panel overflow-hidden p-5 sm:p-6">
          <div class="flex items-start justify-between"><div><p class="text-xs font-bold uppercase tracking-[0.14em] text-[var(--color-brand)]">运维操作</p><h2 class="mt-2 text-lg font-black">过期状态处理</h2></div><span class="grid size-10 place-items-center rounded-xl bg-[var(--color-warning-soft)] text-[var(--color-warning)]"><AppIcon name="operations" /></span></div>
          <p class="mt-2 text-sm leading-6 text-[var(--color-ink-500)]">根据服务器时间统一处理符合条件的包裹和取件码生命周期状态。</p>
          <div class="info-callout mt-4"><AppIcon name="info" :size="18" /><span>此操作只更新逻辑生命周期状态，实际仍有包裹的柜格会保持占用。</span></div>
          <button class="btn-primary mt-5 w-full" type="button" @click="operationOpen = true"><AppIcon name="operations" :size="16" />执行过期处理</button>
          <dl v-if="operationResult" class="mt-5 grid grid-cols-2 gap-4 border-t border-[var(--color-line)] pt-5 text-sm">
            <div class="col-span-2"><dt>本次处理结果</dt><dd>{{ formatDateTime(operationResult.processedAt) }}</dd></div>
            <div><dt>包裹</dt><dd>{{ operationResult.expiredParcels }}</dd></div><div><dt>取件码</dt><dd>{{ operationResult.expiredPickupCodes }}</dd></div>
          </dl>
        </aside>
      </div>
    </div>
    <ConfirmDialog :open="operationOpen" title="执行过期状态处理？" message="服务器将处理当前所有符合条件的包裹和取件码，实际存有包裹的柜格仍会保持占用。" confirm-label="执行处理" :busy="running" @confirm="runExpiration" @cancel="operationOpen = false" />
  </section>
</template>
