<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createLockerCell, getLockerGrid, updateLockerCellStatus } from '../../api/stations'
import { normalizeApiError } from '../../utils/apiError'
import { useAppStore } from '../../stores/app'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import LockerGrid from '../../components/LockerGrid.vue'
import BaseDialog from '../../components/BaseDialog.vue'
import CapacityBar from '../../components/CapacityBar.vue'
import AppIcon from '../../components/AppIcon.vue'
import { sizeText, statusText } from '../../utils/displayText'

const route = useRoute(); const app = useAppStore(); const stationId = computed(() => Number(route.params.stationId))
const grid = ref(null); const loading = ref(true); const refreshing = ref(false); const errorMessage = ref('')
const addOpen = ref(false); const selectedCell = ref(null); const saving = ref(false); const form = reactive({ cellCode: '', size: 'SMALL' }); const formError = ref('')

async function load(refresh = false) { refresh ? (refreshing.value=true) : (loading.value=true); errorMessage.value=''; try { grid.value=await getLockerGrid(stationId.value); if(selectedCell.value) selectedCell.value=grid.value.cells.find((cell)=>cell.id===selectedCell.value.id)||null } catch(error){ errorMessage.value=normalizeApiError(error,'柜格数据加载失败').message } finally { loading.value=false; refreshing.value=false } }
async function addCell(){ saving.value=true; formError.value=''; try { await createLockerCell(stationId.value,{...form}); addOpen.value=false; form.cellCode=''; form.size='SMALL'; app.notify('柜格创建成功','success'); await load(true) } catch(error){ formError.value=normalizeApiError(error,'柜格创建失败').message } finally { saving.value=false } }
function targets(cell){ if(cell.status==='AVAILABLE') return ['MAINTENANCE','DISABLED']; if(cell.status==='MAINTENANCE'||cell.status==='DISABLED') return ['AVAILABLE']; return [] }
async function changeCellStatus(status){ saving.value=true; try { await updateLockerCellStatus(stationId.value,selectedCell.value.id,status); app.notify(`柜格状态已更新为${statusText(status)}`,'success'); await load(true) } catch(error){ app.notify(normalizeApiError(error,'柜格状态更新失败').message,'error') } finally { saving.value=false } }
function statusActionText(status) { return status === 'AVAILABLE' ? '设为空闲' : status === 'MAINTENANCE' ? '设为维护' : status === 'DISABLED' ? '停用' : statusText(status) }
onMounted(()=>load())
</script>

<template>
  <section>
    <PageHeader :title="grid?.station.name || '智能柜格'" eyebrow="实时柜格" :description="grid?.station.address || '正在加载站点信息...'">
      <button class="btn-secondary" type="button" :disabled="refreshing" @click="load(true)"><AppIcon name="refresh" :size="16" />{{ refreshing?'正在刷新...':'刷新' }}</button><button class="btn-primary" type="button" @click="addOpen=true"><AppIcon name="locker" :size="16" />添加柜格</button>
    </PageHeader>
    <LoadingState v-if="loading" class="mt-6" />
    <ErrorState v-else-if="errorMessage" class="mt-6" :message="errorMessage" @retry="load()" />
    <div v-else-if="grid" class="mt-6 space-y-6">
      <div class="panel grid gap-5 p-5 sm:grid-cols-[auto_1fr] sm:items-center sm:p-6"><div><p class="text-[10px] font-bold uppercase tracking-[.12em] text-[var(--color-brand)]">实时容量</p><p class="metric-number mt-1 text-3xl font-black">{{ grid.summary.available }}<span class="ml-1 text-sm font-semibold text-[var(--color-ink-500)]">/ {{ grid.summary.total }} 个空闲</span></p></div><div><CapacityBar :total="grid.summary.total" :available="grid.summary.available" :occupied="grid.summary.occupied" :maintenance="grid.summary.maintenance" :disabled="grid.summary.disabled" /><div class="mt-3 grid grid-cols-4 gap-2 text-center text-[10px] text-[var(--color-ink-500)]"><div v-for="item in [['空闲',grid.summary.available],['已占用',grid.summary.occupied],['维护中',grid.summary.maintenance],['已停用',grid.summary.disabled]]" :key="item[0]"><strong class="block text-sm text-[var(--color-ink-950)]">{{ item[1] }}</strong>{{ item[0] }}</div></div></div></div>
      <div class="panel p-5 sm:p-6"><div class="mb-5 flex items-center justify-between"><div><p class="text-[10px] font-bold uppercase tracking-[.14em] text-[var(--color-brand)]">智能柜格管理</p><h2 class="mt-1 text-lg font-black">柜格</h2></div><StatusBadge :status="grid.station.status" /></div><EmptyState v-if="!grid.cells.length" title="暂无柜格" message="添加第一个柜格开始构建该站点的实时柜格。"><button class="btn-primary" type="button" @click="addOpen=true">添加第一个柜格</button></EmptyState><LockerGrid v-else :cells="grid.cells" @select="selectedCell=$event" /></div>
    </div>
    <BaseDialog :open="addOpen" title="添加柜格" @close="addOpen=false"><form @submit.prevent="addCell"><div v-if="formError" class="error-banner">{{ formError }}</div><label class="form-field mt-4"><span>柜格编号</span><input v-model.trim="form.cellCode" required maxlength="20" /></label><label class="form-field mt-4"><span>柜格尺寸</span><select v-model="form.size"><option value="SMALL">{{ sizeText('SMALL') }}</option><option value="MEDIUM">{{ sizeText('MEDIUM') }}</option><option value="LARGE">{{ sizeText('LARGE') }}</option></select></label><div class="mt-6 flex justify-end gap-3"><button class="btn-secondary" type="button" @click="addOpen=false">取消</button><button class="btn-primary" type="submit" :disabled="saving">{{ saving?'正在保存...':'添加柜格' }}</button></div></form></BaseDialog>
    <BaseDialog :open="Boolean(selectedCell)" title="柜格详情" @close="selectedCell=null"><div v-if="selectedCell"><div class="flex items-center justify-between rounded-[var(--radius-md)] bg-[var(--color-surface)] p-4"><div><p class="text-[10px] font-bold uppercase tracking-[.13em] text-[var(--color-ink-500)]">柜格编号</p><p class="mt-1 text-3xl font-black">{{ selectedCell.cellCode }}</p></div><StatusBadge :status="selectedCell.status" /></div><dl class="mt-5 grid grid-cols-3 gap-4"><div><dt>尺寸</dt><dd>{{ sizeText(selectedCell.size) }}</dd></div><div><dt>数据版本</dt><dd>{{ selectedCell.version }}</dd></div><div><dt>状态</dt><dd>{{ statusText(selectedCell.status) }}</dd></div></dl><div v-if="selectedCell.status==='OCCUPIED'" class="info-callout mt-5"><AppIcon name="info" :size="18" /><span><strong class="block">由系统管理的柜格</strong>此状态由包裹流程自动管理，当前无法手动修改。</span></div><div v-else class="mt-6 border-t border-[var(--color-line)] pt-5"><p class="text-sm font-bold">运维操作</p><div class="mt-3 flex flex-wrap gap-2"><button v-for="target in targets(selectedCell)" :key="target" class="btn-secondary" type="button" :disabled="saving" @click="changeCellStatus(target)">{{ saving?'正在保存...':statusActionText(target) }}</button></div></div></div></BaseDialog>
  </section>
</template>
