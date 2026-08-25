<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { createStation, getStations, updateStation, updateStationStatus } from '../../api/stations'
import { normalizeApiError } from '../../utils/apiError'
import { useAppStore } from '../../stores/app'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import BaseDialog from '../../components/BaseDialog.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'
import CapacityBar from '../../components/CapacityBar.vue'
import AppIcon from '../../components/AppIcon.vue'
import { statusText } from '../../utils/displayText'

const app = useAppStore()
const stations = ref([])
const loading = ref(true)
const errorMessage = ref('')
const filter = ref('ALL')
const formOpen = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = reactive({ name: '', address: '' })
const formError = ref('')
const fieldErrors = ref({})
const pendingStatus = ref(null)

const filteredStations = computed(() => filter.value === 'ALL' ? stations.value : stations.value.filter((station) => station.status === filter.value))

async function load() {
  loading.value = true; errorMessage.value = ''
  try { stations.value = await getStations() }
  catch (error) { errorMessage.value = normalizeApiError(error, '站点数据加载失败').message }
  finally { loading.value = false }
}

function openCreate() { editing.value = null; form.name = ''; form.address = ''; formError.value = ''; fieldErrors.value = {}; formOpen.value = true }
function openEdit(station) { editing.value = station; form.name = station.name; form.address = station.address; formError.value = ''; fieldErrors.value = {}; formOpen.value = true }

async function save() {
  saving.value = true; formError.value = ''; fieldErrors.value = {}
  try {
    if (editing.value) await updateStation(editing.value.id, { ...form })
    else await createStation({ ...form })
    formOpen.value = false
    app.notify(editing.value ? '站点更新成功' : '站点创建成功', 'success')
    await load()
  } catch (error) { const normalized = normalizeApiError(error, '站点保存失败'); formError.value = normalized.message; fieldErrors.value = normalized.fieldErrors }
  finally { saving.value = false }
}

function statusTargets(station) { return station.status === 'ACTIVE' ? ['MAINTENANCE', 'DISABLED'] : ['ACTIVE'] }
function requestStatus(station, status) { if (status === 'MAINTENANCE' || status === 'DISABLED') pendingStatus.value = { station, status }; else changeStatus({ station, status }) }
async function changeStatus(change = pendingStatus.value) {
  if (!change) return
  try { await updateStationStatus(change.station.id, change.status); app.notify(`站点状态已更新为${statusText(change.status)}`, 'success'); pendingStatus.value = null; await load() }
  catch (error) { app.notify(normalizeApiError(error, '站点状态更新失败').message, 'error'); pendingStatus.value = null }
}

function statusActionText(status) { return status === 'ACTIVE' ? '启用' : status === 'MAINTENANCE' ? '设为维护' : status === 'DISABLED' ? '停用' : statusText(status) }

onMounted(load)
</script>

<template>
  <section>
    <PageHeader title="快递柜站点" eyebrow="管理员" description="新建站点、更新站点信息、调整运行状态并查看实时柜格。"><button class="btn-primary" type="button" @click="openCreate"><AppIcon name="stations" :size="16" />新建站点</button></PageHeader>
    <div class="panel mt-6 flex flex-wrap items-end gap-3 p-4"><label class="form-field w-52"><span>状态筛选</span><select v-model="filter"><option value="ALL">全部</option><option value="ACTIVE">正常</option><option value="MAINTENANCE">维护中</option><option value="DISABLED">已停用</option></select></label><button class="btn-secondary" type="button" @click="load"><AppIcon name="refresh" :size="16" />刷新</button><p class="ml-auto self-center text-xs font-semibold text-[var(--color-ink-500)]">显示 {{ filteredStations.length }} / {{ stations.length }} 个站点</p></div>
    <LoadingState v-if="loading" class="mt-6" />
    <ErrorState v-else-if="errorMessage" class="mt-6" :message="errorMessage" @retry="load" />
    <EmptyState v-else-if="!filteredStations.length" class="mt-6" message="没有符合当前状态筛选条件的站点。" />
    <div v-else class="mt-6 grid gap-4 xl:grid-cols-2">
      <article v-for="station in filteredStations" :key="station.id" class="panel panel-interactive overflow-hidden">
        <div class="p-5 sm:p-6"><div class="flex items-start justify-between gap-4"><div class="flex min-w-0 gap-3"><span class="grid size-10 shrink-0 place-items-center rounded-xl bg-[var(--color-brand-50)] text-[var(--color-brand)]"><AppIcon name="stations" /></span><div class="min-w-0"><h2 class="truncate text-lg font-black">{{ station.name }}</h2><p class="mt-1 truncate text-sm text-[var(--color-ink-500)]">{{ station.address }}</p></div></div><StatusBadge :status="station.status" /></div>
        <div class="mt-6 flex items-end justify-between"><div><p class="metric-number text-3xl font-black">{{ station.availableCells }}<span class="ml-1 text-sm font-semibold text-[var(--color-ink-500)]">个空闲</span></p><p class="mt-1 text-xs text-[var(--color-ink-500)]">共 {{ station.totalCells }} 个柜格</p></div><p class="metric-number text-lg font-black text-[var(--color-brand-deep)]">{{ station.totalCells ? Math.round(station.availableCells / station.totalCells * 100) : 0 }}%</p></div>
        <CapacityBar class="mt-4" :total="station.totalCells" :available="station.availableCells" :occupied="station.occupiedCells" :maintenance="station.maintenanceCells" :disabled="station.disabledCells" />
        <div class="mt-4 grid grid-cols-4 gap-2 text-center text-[10px] text-[var(--color-ink-500)]"><div v-for="item in [['空闲',station.availableCells],['已占用',station.occupiedCells],['维护中',station.maintenanceCells],['已停用',station.disabledCells]]" :key="item[0]"><strong class="metric-number block text-sm text-[var(--color-ink-950)]">{{ item[1] }}</strong>{{ item[0] }}</div></div></div>
        <div class="flex flex-wrap gap-2 border-t border-[var(--color-line)] bg-[var(--color-surface)] px-5 py-4"><RouterLink class="btn-primary" :to="`/admin/stations/${station.id}`">查看柜格 <AppIcon name="arrow" :size="15" /></RouterLink><button class="btn-secondary" type="button" @click="openEdit(station)">编辑站点</button><button v-for="target in statusTargets(station)" :key="target" class="btn-secondary" type="button" @click="requestStatus(station,target)">{{ statusActionText(target) }}</button></div>
      </article>
    </div>

    <BaseDialog :open="formOpen" :title="editing ? '编辑站点' : '新建站点'" @close="formOpen = false">
      <form @submit.prevent="save"><div v-if="formError" class="error-banner" role="alert">{{ formError }}</div><label class="form-field mt-4"><span>站点名称</span><input v-model.trim="form.name" required maxlength="100" /><small v-if="fieldErrors.name" class="field-error">{{ fieldErrors.name }}</small></label><label class="form-field mt-4"><span>地址</span><textarea v-model.trim="form.address" required maxlength="255" rows="3"></textarea><small v-if="fieldErrors.address" class="field-error">{{ fieldErrors.address }}</small></label><div class="mt-6 flex justify-end gap-3"><button class="btn-secondary" type="button" @click="formOpen=false">取消</button><button class="btn-primary" type="submit" :disabled="saving">{{ saving ? '正在保存...' : '保存站点' }}</button></div></form>
    </BaseDialog>
    <ConfirmDialog :open="Boolean(pendingStatus)" :title="`确认将站点${statusActionText(pendingStatus?.status)}？`" message="如果站点包含已占用柜格或仍有关联的活动包裹，系统可能拒绝此次状态变更。" confirm-label="确认变更" @confirm="changeStatus()" @cancel="pendingStatus=null" />
  </section>
</template>
