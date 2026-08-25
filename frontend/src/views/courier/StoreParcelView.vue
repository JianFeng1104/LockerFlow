<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getStations } from '../../api/stations'
import { storeParcel } from '../../api/parcels'
import { normalizeApiError } from '../../utils/apiError'
import { formatDateTime, formatRelativeExpiry } from '../../utils/format'
import { useAppStore } from '../../stores/app'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import ErrorState from '../../components/ErrorState.vue'
import BaseDialog from '../../components/BaseDialog.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import AppIcon from '../../components/AppIcon.vue'
import { sizeText } from '../../utils/displayText'

const app=useAppStore(); const stations=ref([]); const loading=ref(true); const loadingError=ref(''); const storing=ref(false); const errorMessage=ref(''); const fieldErrors=ref({}); const result=ref(null)
const form=reactive({trackingNumber:'',customerId:'',stationId:'',size:'SMALL'})
async function loadStations(){loading.value=true;loadingError.value='';try{stations.value=await getStations('ACTIVE')}catch(error){loadingError.value=normalizeApiError(error,'可用站点加载失败').message}finally{loading.value=false}}
async function submit(){storing.value=true;errorMessage.value='';fieldErrors.value={};try{result.value=await storeParcel({trackingNumber:form.trackingNumber,customerId:Number(form.customerId),stationId:Number(form.stationId),size:form.size});form.trackingNumber='';app.notify('包裹入柜成功','success')}catch(error){const normalized=normalizeApiError(error,'包裹入柜失败');errorMessage.value=normalized.message;fieldErrors.value=normalized.fieldErrors}finally{storing.value=false}}
async function copyCode(){if(!result.value?.pickupCode)return;try{await navigator.clipboard.writeText(result.value.pickupCode);app.notify('取件码已复制','success')}catch{app.notify('无法访问剪贴板','error')}}
function closeResult(){result.value=null}
onMounted(loadStations)
</script>

<template>
  <section>
    <PageHeader title="包裹入柜" eyebrow="快递员" description="快递员身份来自当前登录会话，系统将自动分配最合适的空闲柜格。" />
    <LoadingState v-if="loading" class="mt-6" message="正在加载站点..." />
    <ErrorState v-else-if="loadingError" class="mt-6" :message="loadingError" @retry="loadStations" />
    <div v-else class="mt-6 grid gap-6 xl:grid-cols-[minmax(0,.68fr)_minmax(18rem,.32fr)]">
      <form class="panel p-6 sm:p-7" @submit.prevent="submit">
        <div class="flex items-start justify-between gap-4">
          <div><p class="text-[10px] font-bold uppercase tracking-[.14em] text-[var(--color-brand)]">包裹信息</p><h2 class="mt-1 text-xl font-black">入柜申请</h2></div>
          <span class="grid size-10 place-items-center rounded-xl bg-[var(--color-brand-50)] text-[var(--color-brand)]"><AppIcon name="store" /></span>
        </div>
        <div v-if="errorMessage" class="error-banner mt-5" role="alert">{{ errorMessage }}</div>
        <label class="form-field mt-5"><span>快递单号</span><input v-model.trim="form.trackingNumber" required maxlength="64" placeholder="例如：PKG-2026-0001" /><small v-if="fieldErrors.trackingNumber" class="field-error">{{ fieldErrors.trackingNumber }}</small></label>
        <label class="form-field mt-4"><span>用户 ID</span><input v-model="form.customerId" type="number" min="1" step="1" required inputmode="numeric" placeholder="请输入正整数 ID" /><small>当前流程需要填写接收包裹的用户 ID。</small><small v-if="fieldErrors.customerId" class="field-error">{{ fieldErrors.customerId }}</small></label>
        <div class="mt-4 grid gap-4 sm:grid-cols-2">
          <label class="form-field"><span>快递柜站点</span><select v-model="form.stationId" required><option disabled value="">请选择站点</option><option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option></select></label>
          <label class="form-field"><span>包裹尺寸</span><select v-model="form.size"><option value="SMALL">{{ sizeText('SMALL') }}</option><option value="MEDIUM">{{ sizeText('MEDIUM') }}</option><option value="LARGE">{{ sizeText('LARGE') }}</option></select></label>
        </div>
        <button class="btn-primary mt-6" type="submit" :disabled="storing || !stations.length"><AppIcon name="store" :size="16" />{{ storing ? '正在入柜...' : '确认入柜' }}</button>
      </form>
      <aside class="panel surface-brand p-5 sm:p-6">
        <p class="text-[10px] font-bold uppercase tracking-[.14em] text-[var(--color-brand-deep)]">柜格分配流程</p>
        <h2 class="mt-2 text-lg font-black">最佳适配柜格分配</h2>
        <p class="mt-2 text-sm leading-6 text-[var(--color-ink-500)]">系统将根据当前包裹信息和实时柜格容量作出最终分配。</p>
        <div class="workflow-steps mt-5"><div v-for="(step,index) in [{title:'包裹',note:'快递单号、用户和尺寸'},{title:'站点',note:'验证站点是否正常'},{title:'最佳适配',note:'匹配可用柜格容量'},{title:'柜格',note:'事务成功后完成分配'}]" :key="step.title" class="workflow-step"><span class="workflow-index">{{ index + 1 }}</span><span><strong>{{ step.title }}</strong><small>{{ step.note }}</small></span><AppIcon v-if="index < 3" name="arrow" :size="15" /></div></div>
        <div class="info-callout mt-5"><AppIcon name="info" :size="18" /><span>页面不会预先推测柜格编号，只有真实入柜事务成功后才会显示分配结果。</span></div>
        <p v-if="!stations.length" class="error-banner mt-4">当前没有可用的正常站点。</p>
      </aside>
    </div>

    <BaseDialog :open="Boolean(result)" title="包裹入柜成功" @close="closeResult">
      <div v-if="result">
        <div class="flex items-center justify-between gap-4"><div><p class="text-xs font-black uppercase tracking-[.15em] text-[var(--color-success)]">入柜成功</p><p class="tracking-code mt-1 text-lg">{{ result.parcel.trackingNumber }}</p></div><StatusBadge status="STORED" /></div>
        <div class="code-display mt-5"><span class="mx-auto grid size-10 place-items-center rounded-full bg-white text-[var(--color-success)] shadow-[var(--shadow-sm)]"><AppIcon name="check" :size="21" /></span><p class="mt-3 text-xs font-black uppercase tracking-[0.16em] text-[var(--color-brand-deep)]">一次性取件码</p><p class="code-value mt-3 font-mono text-4xl font-black tracking-[0.2em] sm:text-5xl">{{ result.pickupCode }}</p><p class="mt-3 text-sm font-semibold">此取件码仅显示一次，请立即复制并妥善告知用户。</p><button class="btn-primary mt-4" type="button" @click="copyCode">复制取件码</button></div>
        <dl class="mt-5 grid grid-cols-2 gap-4 rounded-[var(--radius-sm)] bg-[var(--color-surface)] p-4"><div><dt>已分配站点</dt><dd>{{ result.parcel.stationName }}</dd></div><div><dt>已分配柜格</dt><dd class="text-lg">{{ result.parcel.lockerCellCode }} · {{ sizeText(result.parcel.lockerCellSize) }}</dd></div><div class="col-span-2"><dt>取件截止时间</dt><dd>{{ formatDateTime(result.pickupCodeExpiresAt || result.parcel.expiresAt) }}</dd><p class="expiry-relative mt-1">{{ formatRelativeExpiry(result.pickupCodeExpiresAt || result.parcel.expiresAt) }}</p></div></dl>
        <button class="btn-secondary mt-6 w-full" type="button" @click="closeResult">关闭并清除取件码</button>
      </div>
    </BaseDialog>
  </section>
</template>
