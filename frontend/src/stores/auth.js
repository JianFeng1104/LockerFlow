import { defineStore } from 'pinia'
import * as authApi from '../api/auth'
import { clearStoredSession, getStoredSession, saveStoredSession } from '../utils/authStorage'
import { normalizeApiError } from '../utils/apiError'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    accessToken: null,
    expiresAt: null,
    initialized: false,
    loading: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.user && state.accessToken),
    role: (state) => state.user?.role ?? null,
  },
  actions: {
    async login(credentials) {
      this.loading = true
      try {
        const response = await authApi.login(credentials)
        this.user = response.user
        this.accessToken = response.accessToken
        this.expiresAt = response.expiresAt
        saveStoredSession(response)
        return response.user
      } finally {
        this.loading = false
        this.initialized = true
      }
    },
    async loadCurrentUser() {
      this.user = await authApi.getCurrentUser()
      return this.user
    },
    async restoreSession() {
      if (this.initialized) return
      this.loading = true
      const session = getStoredSession()
      if (!session) {
        this.clearSession()
        this.initialized = true
        this.loading = false
        return
      }
      this.accessToken = session.accessToken
      this.expiresAt = session.expiresAt
      try {
        await this.loadCurrentUser()
      } catch (error) {
        if (normalizeApiError(error).status === 401) this.clearSession()
        throw error
      } finally {
        this.initialized = true
        this.loading = false
      }
    },
    clearSession() {
      this.user = null
      this.accessToken = null
      this.expiresAt = null
      clearStoredSession()
    },
    logout() {
      this.clearSession()
      this.initialized = true
    },
  },
})
