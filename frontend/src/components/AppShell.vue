<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppIcon from './AppIcon.vue'
import BrandMark from './BrandMark.vue'
import { roleText } from '../utils/displayText'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mobileOpen = ref(false)

const menus = {
  ADMIN: [
    { label: '工作台', to: '/admin', icon: 'dashboard' },
    { label: '快递柜站点', to: '/admin/stations', icon: 'stations' },
  ],
  COURIER: [
    { label: '工作台', to: '/courier', icon: 'dashboard' },
    { label: '包裹入柜', to: '/courier/store', icon: 'store' },
    { label: '我的包裹', to: '/courier/parcels', icon: 'parcels' },
  ],
  CUSTOMER: [
    { label: '工作台', to: '/customer', icon: 'dashboard' },
    { label: '我的包裹', to: '/customer/parcels', icon: 'parcels' },
  ],
}

const navigation = computed(() => menus[auth.role] || [])
const roleLabel = computed(() => roleText(auth.role))
const initials = computed(() => (auth.user?.username || 'LF')
  .split(/[._\-\s]+/)
  .filter(Boolean)
  .slice(0, 2)
  .map((part) => part[0])
  .join('')
  .toUpperCase())

function isActive(item) {
  if (route.path === item.to) return true
  const isDashboard = navigation.value[0]?.to === item.to
  return !isDashboard && route.path.startsWith(`${item.to}/`)
}

function closeMobile() {
  mobileOpen.value = false
}

function handleKeydown(event) {
  if (event.key === 'Escape' && mobileOpen.value) closeMobile()
}

watch(() => route.fullPath, closeMobile)
onMounted(() => document.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <button v-if="mobileOpen" class="sidebar-overlay" type="button" aria-label="关闭导航菜单" @click="closeMobile"></button>
    <aside id="primary-sidebar" class="app-sidebar" :class="{ 'is-open': mobileOpen }">
      <div class="sidebar-brand">
        <RouterLink class="brand-link" :to="navigation[0]?.to || '/'" @click="closeMobile">
          <BrandMark />
          <span><strong>LockerFlow</strong><small>智能快递柜管理平台</small></span>
        </RouterLink>
        <button class="sidebar-close icon-button" type="button" aria-label="关闭导航菜单" @click="closeMobile"><AppIcon name="close" /></button>
      </div>

      <div class="sidebar-context">
        <span class="context-pulse" aria-hidden="true"></span>
        <div><small>当前工作空间</small><strong>{{ roleLabel }}</strong></div>
      </div>

      <nav class="sidebar-nav" aria-label="主导航">
        <p class="sidebar-label">工作空间</p>
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to" class="nav-link" :class="{ 'is-active': isActive(item) }" :aria-current="isActive(item) ? 'page' : undefined" @click="closeMobile">
          <AppIcon :name="item.icon" /><span>{{ item.label }}</span><span class="nav-indicator" aria-hidden="true"></span>
        </RouterLink>
      </nav>

      <div class="sidebar-footer">
        <div class="sidebar-user"><span class="avatar avatar-dark">{{ initials }}</span><div><strong>{{ auth.user?.username }}</strong><small>{{ roleLabel }}</small></div></div>
        <button class="sidebar-logout" type="button" @click="logout"><AppIcon name="logout" /><span>退出登录</span></button>
        <p>安全的角色权限工作空间</p>
      </div>
    </aside>

    <div class="app-workspace">
      <header class="app-topbar">
        <div class="topbar-heading">
          <button class="mobile-menu icon-button" type="button" aria-label="打开导航菜单" aria-controls="primary-sidebar" :aria-expanded="mobileOpen" @click="mobileOpen = true"><AppIcon name="menu" /></button>
          <div><p>LockerFlow <span>/</span> {{ roleLabel }}</p><h2>{{ route.meta.title }}</h2></div>
        </div>
        <div class="topbar-account">
          <span class="avatar">{{ initials }}</span>
          <div class="account-copy"><strong>{{ auth.user?.username }}</strong><small>{{ roleLabel }}</small></div>
          <span class="role-pill">{{ roleLabel }}</span>
          <button class="topbar-logout icon-button" type="button" aria-label="退出登录" title="退出登录" @click="logout"><AppIcon name="logout" /></button>
        </div>
      </header>
      <main class="app-content"><RouterView /></main>
    </div>
  </div>
</template>
