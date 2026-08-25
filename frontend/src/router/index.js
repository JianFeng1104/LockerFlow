import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const rolePaths = {
  ADMIN: '/admin',
  COURIER: '/courier',
  CUSTOMER: '/customer',
}

export function roleHome(role) {
  return rolePaths[role] || '/login'
}

export const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  {
    path: '/admin',
    component: () => import('../components/AppShell.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] },
    children: [
      { path: '', name: 'admin-dashboard', component: () => import('../views/admin/AdminDashboardView.vue'), meta: { title: '管理员工作台' } },
      { path: 'stations', name: 'admin-stations', component: () => import('../views/admin/StationsView.vue'), meta: { title: '快递柜站点' } },
      { path: 'stations/:stationId', name: 'admin-station-grid', component: () => import('../views/admin/StationGridView.vue'), meta: { title: '智能柜格' } },
    ],
  },
  {
    path: '/courier',
    component: () => import('../components/AppShell.vue'),
    meta: { requiresAuth: true, roles: ['COURIER'] },
    children: [
      { path: '', name: 'courier-dashboard', component: () => import('../views/courier/CourierDashboardView.vue'), meta: { title: '快递员工作台' } },
      { path: 'store', name: 'courier-store', component: () => import('../views/courier/StoreParcelView.vue'), meta: { title: '包裹入柜' } },
      { path: 'parcels', name: 'courier-parcels', component: () => import('../views/courier/CourierParcelsView.vue'), meta: { title: '我的包裹' } },
    ],
  },
  {
    path: '/customer',
    component: () => import('../components/AppShell.vue'),
    meta: { requiresAuth: true, roles: ['CUSTOMER'] },
    children: [
      { path: '', name: 'customer-dashboard', component: () => import('../views/customer/CustomerDashboardView.vue'), meta: { title: '用户工作台' } },
      { path: 'parcels', name: 'customer-parcels', component: () => import('../views/customer/CustomerParcelsView.vue'), meta: { title: '我的包裹' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', redirect: '/' },
]

export function resolvePostLoginRedirect(router, redirect, role) {
  const fallback = roleHome(role)
  if (typeof redirect !== 'string' || !redirect.startsWith('/') || redirect.startsWith('//')) return fallback
  const resolved = router.resolve(redirect)
  if (!resolved.matched.length || resolved.name === 'login' || resolved.name === 'not-found') return fallback
  const allowedRoles = resolved.meta.roles
  return !allowedRoles || allowedRoles.includes(role) ? resolved.fullPath : fallback
}

export function installAuthGuard(router) {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    if (!auth.initialized) {
      try {
        await auth.restoreSession()
      } catch {
        // The destination checks below decide where an unavailable session belongs.
      }
    }

    if (to.name === 'home') return auth.isAuthenticated ? roleHome(auth.role) : '/login'
    if (to.name === 'login' && auth.isAuthenticated) return roleHome(auth.role)
    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (to.meta.roles && !to.meta.roles.includes(auth.role)) return roleHome(auth.role)
    return true
  })
}

export function createAppRouter(history = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({ history, routes })
  installAuthGuard(router)
  return router
}

export default createAppRouter()
