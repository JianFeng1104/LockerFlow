<script setup>
import { computed, onMounted, ref } from 'vue'
import { getCustomerParcels, pickupParcel } from '../../api/parcels'
import { normalizeApiError } from '../../utils/apiError'
import { useAppStore } from '../../stores/app'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import ErrorState from '../../components/ErrorState.vue'
import EmptyState from '../../components/EmptyState.vue'
import CustomerParcelList from '../../components/CustomerParcelList.vue'
import BaseDialog from '../../components/BaseDialog.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import AppIcon from '../../components/AppIcon.vue'
import { formatDateTime, formatRelativeExpiry } from '../../utils/format'

const app=useAppStore();const parcels=ref([]);const loading=ref(true);const refreshing=ref(false);const errorMessage=ref('');const filter=ref('ALL');const selected=ref(null);const pickupCode=ref('');const pickupError=ref('');const picking=ref(false)
const filtered=computed(()=>filter.value==='ALL'?parcels.value:parcels.value.filter(p=>p.status===filter.value))
async function load(refresh=false){refresh?(refreshing.value=true):(loading.value=true);errorMessage.value='';try{parcels.value=await getCustomerParcels()}catch(error){errorMessage.value=normalizeApiError(error,'包裹数据加载失败').message}finally{loading.value=false;refreshing.value=false}}
function openPickup(parcel){selected.value=parcel;pickupCode.value='';pickupError.value=''}
function closePickup(){selected.value=null;pickupCode.value='';pickupError.value=''}
async function submitPickup(){if(!/^\d{6}$/.test(pickupCode.value)){pickupError.value='取件码必须为 6 位数字';return}picking.value=true;pickupError.value='';try{await pickupParcel(selected.value.id,pickupCode.value);closePickup();app.notify('取件成功','success');await load(true)}catch(error){pickupError.value=normalizeApiError(error,'取件码无效或已过期').message}finally{picking.value=false}}
onMounted(()=>load())
</script>

<template><section><PageHeader title="我的包裹" eyebrow="用户" description="包裹处于待取件状态且仍在有效取件时间内时，可以输入取件码完成取件。"><button class="btn-secondary" type="button" :disabled="refreshing" @click="load(true)"><AppIcon name="refresh" :size="16"/>{{refreshing?'正在刷新...':'刷新'}}</button></PageHeader><div class="panel mt-6 flex flex-wrap items-end gap-3 p-4"><label class="form-field w-52"><span>状态筛选</span><select v-model="filter"><option value="ALL">全部状态</option><option value="STORED">待取件</option><option value="PICKED_UP">已取件</option><option value="EXPIRED">已过期</option><option value="CANCELLED">已取消</option></select></label><p class="ml-auto self-center text-xs font-semibold text-[var(--color-ink-500)]">显示 {{filtered.length}} / {{parcels.length}} 个包裹</p></div><LoadingState v-if="loading" class="mt-6" message="正在加载包裹..."/><ErrorState v-else-if="errorMessage" class="mt-6" :message="errorMessage" @retry="load()"/><EmptyState v-else-if="!filtered.length" class="mt-6" message="没有符合当前筛选条件的包裹。"/><CustomerParcelList v-else class="mt-6" :parcels="filtered" @pickup="openPickup"/><BaseDialog :open="Boolean(selected)" title="包裹取件" @close="closePickup"><form @submit.prevent="submitPickup"><div v-if="selected" class="rounded-[var(--radius-sm)] bg-[var(--color-surface)] p-4"><div class="flex items-start justify-between gap-3"><div><p class="text-[10px] font-bold uppercase tracking-[.12em] text-[var(--color-ink-500)]">包裹</p><p class="tracking-code mt-1">{{selected.trackingNumber}}</p></div><StatusBadge :status="selected.status"/></div><dl class="mt-4 grid grid-cols-2 gap-4 text-sm"><div><dt>快递柜站点</dt><dd>{{selected.stationName||'—'}}</dd></div><div><dt>柜格</dt><dd class="text-lg">{{selected.lockerCellCode||'—'}}</dd></div><div class="col-span-2"><dt>取件截止时间</dt><dd>{{formatDateTime(selected.expiresAt)}}</dd><p class="expiry-relative mt-1">{{formatRelativeExpiry(selected.expiresAt)}}</p></div></dl></div><p class="mt-5 text-sm leading-6 text-[var(--color-ink-500)]">请输入 6 位一次性取件码，用户身份以当前登录会话为准。</p><div v-if="pickupError" id="pickup-error" class="error-banner mt-4" role="alert">{{pickupError}}</div><label class="form-field mt-5"><span>取件码</span><input v-model="pickupCode" class="text-center font-mono text-xl tracking-[.25em]" inputmode="numeric" maxlength="6" pattern="\d{6}" autocomplete="one-time-code" required placeholder="000000" :aria-describedby="pickupError?'pickup-error':undefined"/></label><div class="mt-6 flex justify-end gap-3"><button class="btn-secondary" type="button" :disabled="picking" @click="closePickup">取消</button><button class="btn-primary" type="submit" :disabled="picking"><AppIcon name="check" :size="16"/>{{picking?'正在取件...':'确认取件'}}</button></div></form></BaseDialog></section></template>
