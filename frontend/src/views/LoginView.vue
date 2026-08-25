<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { normalizeApiError } from '../utils/apiError'
import { resolvePostLoginRedirect } from '../router'
import BrandMark from '../components/BrandMark.vue'
import AppIcon from '../components/AppIcon.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const credentials = reactive({ username: '', password: '' })
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  try {
    const user = await auth.login({ ...credentials })
    credentials.password = ''
    await router.replace(resolvePostLoginRedirect(router, route.query.redirect, user.role))
  } catch (error) {
    credentials.password = ''
    errorMessage.value = normalizeApiError(error, '用户名或密码错误').message
  }
}
</script>

<template>
  <main class="login-page grid min-h-screen place-items-center px-5 py-10">
    <section class="login-shell grid w-full max-w-5xl overflow-hidden rounded-[var(--radius-lg)] border border-white bg-white shadow-[var(--shadow-lg)] lg:grid-cols-[1fr_0.82fr]">
      <div class="login-hero hidden p-12 text-white lg:block">
        <div class="flex items-center gap-3"><BrandMark /><div><strong class="block">LockerFlow</strong><small class="text-[10px] font-bold uppercase tracking-[.14em] text-slate-400">智能快递柜管理平台</small></div></div>
        <div class="hero-lockers" aria-hidden="true"><span v-for="index in 12" :key="index"><i></i></span></div>
        <p class="mt-12 text-xs font-bold uppercase tracking-[0.2em] text-emerald-300">智能包裹管理</p>
        <h1 class="mt-4 text-5xl font-black leading-[1.04] tracking-[-0.05em]">让每一次存取都安全流畅。</h1>
        <p class="mt-6 max-w-md leading-7 text-slate-300">通过 LockerFlow 管理快递柜站点、完成包裹入柜，并为用户提供安全的身份绑定取件流程。</p>
      </div>
      <form class="login-form p-7 sm:p-12" @submit.prevent="submit">
        <div class="flex items-center gap-3 lg:hidden"><BrandMark /><strong>LockerFlow</strong></div>
        <p class="mt-8 text-xs font-bold uppercase tracking-[0.18em] text-[var(--color-brand)] lg:mt-0">安全工作空间</p>
        <h2 class="mt-3 text-3xl font-black tracking-[-0.04em]">欢迎回来</h2>
        <p class="mt-2 text-sm text-[var(--color-ink-500)]">登录 LockerFlow 智能快递柜管理平台。</p>

        <div v-if="errorMessage" id="login-error" class="error-banner mt-6" role="alert">{{ errorMessage }}</div>
        <label class="form-field mt-8">
          <span>用户名</span>
          <input v-model.trim="credentials.username" name="username" autocomplete="username" required maxlength="50" :aria-describedby="errorMessage ? 'login-error' : undefined" />
        </label>
        <label class="form-field mt-5">
          <span>密码</span>
          <input v-model="credentials.password" name="password" type="password" autocomplete="current-password" required maxlength="128" :aria-describedby="errorMessage ? 'login-error' : undefined" />
        </label>
        <button class="btn-primary mt-7 w-full" type="submit" :disabled="auth.loading">{{ auth.loading ? '正在登录...' : '登录' }}<AppIcon v-if="!auth.loading" name="arrow" :size="17" /></button>
        <p class="mt-5 text-xs leading-5 text-[var(--color-ink-500)]">访问权限由后端 JWT 和角色校验进行验证，本页面不会保存你的密码。</p>
      </form>
    </section>
  </main>
</template>
