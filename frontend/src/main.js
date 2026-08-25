import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { configureHttpAuth } from './api/http'
import { useAuthStore } from './stores/auth'
import './styles/main.css'

const pinia = createPinia()
const app = createApp(App)
app.use(pinia)

const auth = useAuthStore(pinia)
configureHttpAuth({
  onUnauthorized: () => {
    auth.clearSession()
    if (router.currentRoute.value.name !== 'login') {
      router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  },
})

app.use(router).mount('#app')
