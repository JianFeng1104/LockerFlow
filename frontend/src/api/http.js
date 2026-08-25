import axios from 'axios'
import { clearStoredSession, getStoredSession } from '../utils/authStorage'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

let unauthorizedHandler = null

export function configureHttpAuth({ onUnauthorized } = {}) {
  unauthorizedHandler = typeof onUnauthorized === 'function' ? onUnauthorized : null
}

http.interceptors.request.use((config) => {
  const isLoginRequest = config.url?.endsWith('/auth/login')
  const session = isLoginRequest ? null : getStoredSession()
  if (session?.accessToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${session.accessToken}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const isLoginRequest = error.config?.url?.endsWith('/auth/login')
    if (status === 401 && !isLoginRequest) {
      clearStoredSession()
      unauthorizedHandler?.()
    }
    return Promise.reject(error)
  },
)

export default http
